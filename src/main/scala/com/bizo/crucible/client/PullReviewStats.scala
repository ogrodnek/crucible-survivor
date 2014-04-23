package com.bizo.crucible.client

import com.bizo.crucible.client.model._
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.logging.Logger
import net.liftweb.json.DefaultFormats
import net.liftweb.json.Serialization.write
import java.util.TimeZone

/**
 * Pull review stats from Crucible and pushes to S3.
 *
 * Must set the following Environment variables (or java System properties):
 *
 * for Crucible REST API access --
 *   * CRUCIBLE_HOST
 *   * CRUCIBLE_USER
 *   * CRUCIBLE_PASS
 */
object PullReviewStats {
  val logger = Logger.getLogger(getClass.getName)

  lazy val client: CrucibleAPI = new CrucibleAPIClient(Env("CRUCIBLE_HOST"), new EnvironmentCredentialsProvider)

  def main(args: Array[String]) {
    val outputFile = if (args.length < 1) {
      sys.error("Usage: <outputFile>")
    } else {
      args(0)
    }

    val (openReviewsToConsider, openReviewDetails) = pullDetails("Review", 0)
    val (closedReviewsToConsider, closedReviewDetails) = pullDetails("Closed", 1)

    val (winners, losers) = getLeaderBoard(openReviewDetails, closedReviewDetails)

    val df = new SimpleDateFormat("E MM.dd hh:mm a")
    df.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));

    val stats = new ReviewLeaderStats(
      winners,
      losers,
      openReviewsToConsider.size,
      df.format(new java.util.Date),
      getOpenCloseStats(openReviewsToConsider, closedReviewsToConsider),
      getOpenCountStats(openReviewsToConsider, closedReviewsToConsider))

    val out = writeToFile(outputFile, stats)
    System.err.println("stats written to: " + out.getAbsolutePath)
  }

  private def writeToFile(file: String, stats: ReviewLeaderStats): java.io.File = {
    implicit val formats = new DefaultFormats {}

    val of = new java.io.File(file)
    val os = new OutputStreamWriter(new FileOutputStream(of))

    os.write("var leaderStats=");
    write(stats, os)
    os.close()

    of
  }

  private def pullDetails(reviewState: String, numMonths: Int) = {
    logger.info("Pulling review for state: " + reviewState)

    val ret = client.getReviewsInState(reviewState)

    logger.info("Found %d reviews for state %s..".format(ret.size, reviewState))

    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, -(numMonths))
    val cutOff = c.getTime
    val count = new java.util.concurrent.atomic.AtomicInteger

    val reviewsToConsider =
      if (numMonths <= 0)
        ret
      else
        (ret filter { r =>
          cutOff.compareTo(r.createDate) < 0
        })

    logger.info("considering %d reviews for last %d month(s).".format(reviewsToConsider.size, numMonths))

    val reviewDetails = (reviewsToConsider.par map { r =>
      System.err.print(".")
      if (count.incrementAndGet() % 25 == 0) {
        System.err.println()
      }
      client.getReview(r.permaId)
    }).seq

    (reviewsToConsider, reviewDetails)
  }

  val reportDateFormat = new SimpleDateFormat("yyyy-MM-dd")

  private def getOpenCountStats(open: Seq[ReviewSummary], closed: Seq[ReviewSummary], numDays: Int = 14): Seq[Array[Any]] = {
    val dates = (open.flatMap(r => getDaysUntilToday(r.createDate).map(reportDateFormat.format(_))) ++
      closed.flatMap(r => getDaysUntil(r.createDate, r.closeDate.get)).map(reportDateFormat.format(_)))

    val ds = (dates.groupBy(d => d).map { d => (d._1, d._2.size) }).toSeq

    val r = ds.sortBy(_._1).takeRight(numDays)

    r.map(s => Array(s._1, s._2))
  }

  def getDaysUntilToday(d: Date): List[Date] = {
    getDaysUntil(d, new java.util.Date())
  }

  def clearTimeComponents(d: Date): Date = {
    import Calendar._
    val c = Calendar.getInstance()
    c.setTime(d)

    for (f <- Seq(HOUR_OF_DAY, MINUTE, SECOND, MILLISECOND)) {
      c.set(f, 0)
    }

    c.getTime
  }

  private def getDaysUntil(_d: Date, _end: Date): List[Date] = {
    val end = clearTimeComponents(_end)
    val d = clearTimeComponents(_d)

    if (d.compareTo(end) <= 0) {
      val next = Calendar.getInstance
      next.setTime(d)
      next.add(Calendar.DAY_OF_YEAR, 1)

      d :: getDaysUntil(next.getTime, end)
    } else {
      List()
    }
  }

  private def getOpenCloseStats(open: Seq[ReviewSummary], closed: Seq[ReviewSummary]): Seq[Array[Any]] = {
    val openStats = getReviewStatsByDate(open, { _.createDate })
    val closedStats = getReviewStatsByDate(closed, { _.closeDate.get })

    val dates = (0 to 7).reverse map { v =>
      val cal = Calendar.getInstance
      cal.add(Calendar.DAY_OF_YEAR, -(v))
      reportDateFormat.format(cal.getTime)
    }

    dates map { d =>
      Array(d, openStats.getOrElse(d, 0), closedStats.getOrElse(d, 0))
    }
  }

  private def getReviewStatsByDate(reviews: Seq[ReviewSummary], df: (ReviewSummary) => Date, num: Int = 7): Map[String, Int] = {
    val a = reviews.map(df).map(reportDateFormat.format(_)).groupBy { r => r }

    (a.map {
      case (date, num) =>
        (date -> num.size)
    })
  }

  private def getLeaderBoard(openReviews: Seq[ReviewResponse], closedReviews: Seq[ReviewResponse], num: Int = 5) = {
    val a = (openReviews ++ closedReviews).flatMap(_.reviewer).groupBy(_.userName)

    val reviewerStats = (a.map {
      case (reviewer, state) =>
        (reviewer -> state.groupBy(_.completed).map(s => (s._1 -> s._2.size)))
    }).toSeq

    val winners = reviewerStats.sortBy(_._2.get(true)).reverse
    val losers = reviewerStats.sortBy(_._2.get(false)).reverse

    val users = client.getUsers

    (
      winners.take(5).map {
        case (u, stats) =>
          if (users.contains(u))
            new ReviewLeaderUser(u, users(u).avatarUrl, stats.getOrElse(true, 0))
          else
            ReviewLeaderMissingUser(u, stats.getOrElse(false, 0))
      },
      losers.take(5).map {
        case (u, stats) =>
          if (users.contains(u))
            new ReviewLeaderUser(u, users(u).avatarUrl, stats.getOrElse(false, 0))
          else
            ReviewLeaderMissingUser(u, stats.getOrElse(false, 0))
      })
  }

  def ReviewLeaderMissingUser(name: String, reviews: Int) = ReviewLeaderUser(
    name,
    "http://gravatar.com/avatar/00000000000000000000000000000000?d=retro&s=48",
    reviews)
}

case class ReviewLeaderStats(
  fame: Seq[ReviewLeaderUser],
  shame: Seq[ReviewLeaderUser],
  totalOpenReviews: Int,
  updateDate: String,
  openCloseStats: Seq[Array[Any]],
  openCountStats: Seq[Array[Any]])

case class ReviewLeaderUser(name: String, avatarUrl: String, reviews: Int)
