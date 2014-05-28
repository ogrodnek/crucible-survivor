package com.bizo.crucible.survivor

import com.bizo.crucible.client.model._
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.Logger
import net.liftweb.json.DefaultFormats
import net.liftweb.json.Serialization.write
import java.util.TimeZone
import com.bizo.crucible.client._
import DateUtils._
import com.bizo.crucible.survivor.scoring.Scoring
import com.bizo.crucible.survivor.scoring.impl.CompoundOpenClosedScoring
import com.bizo.crucible.survivor.scoring.LeaderBoardRow
import com.sun.corba.se.impl.ior.OldJIDLObjectKeyTemplate

/**
 * Pull review stats from Crucible.
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
    
    logger.info("Pulling open reviews....")
    val openReviewDetails = client.getReviewDetailsWithFilter(PredefinedReviewFilter.global.allOpenReviews)
    val recentOpenReviewDetails = filterReviewsByMonth(openReviewDetails, 1)
    
    val closedReviewDetails = {
      logger.info("Pulling closed reviews...")
      val f = ReviewFilter(states = Seq(ReviewState.Closed), fromDate = Some(monthsAgo(1).getTime))
      
      // API seems to return reviews that have had any activiy in past month, but what we want is created
      // in last month, so still need to filter
      filterReviewsByMonth(client.getReviewDetailsWithFilter(f), 1)
    }
    
    logger.info("Generating leaderboard...")
    
    val (winners, losers) = getLeaderBoard(openReviewDetails, closedReviewDetails, recentOpenReviewDetails)

    val df = new SimpleDateFormat("E MM.dd hh:mm a")
    df.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));

    val stats = new ReviewLeaderStats(
      winners,
      losers,
      openReviewDetails.size,
      df.format(new java.util.Date),
      getOpenCloseStats(openReviewDetails, closedReviewDetails),
      getOpenCountStats(openReviewDetails, closedReviewDetails))

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
  
  private def filterReviewsByMonth[T<:Review](reviews: Seq[T], numMonths: Int): Seq[T] = {
    if (numMonths <= 0)
      reviews
    else {
      val cutOff = monthsAgo(numMonths)      
      reviews filter { r =>
        cutOff.compareTo(r.createDate) < 0
      }
    }
  }


  val reportDateFormat = new SimpleDateFormat("yyyy-MM-dd")

  private def getOpenCountStats(open: Seq[Review], closed: Seq[Review], numDays: Int = 14): Seq[Array[Any]] = {
    val dates = (open.flatMap(r => getDaysUntilToday(r.createDate).map(reportDateFormat.format(_))) ++
      closed.flatMap(r => getDaysUntil(r.createDate, r.closeDate.get)).map(reportDateFormat.format(_)))

    val ds = (dates.groupBy(d => d).map { d => (d._1, d._2.size) }).toSeq

    val r = ds.sortBy(_._1).takeRight(numDays)

    r.map(s => Array(s._1, s._2))
  }

  private def getOpenCloseStats(open: Seq[Review], closed: Seq[Review]): Seq[Array[Any]] = {
    val openStats = getReviewStatsByDate(open, { _.createDate })
    val closedStats = getReviewStatsByDate(closed, { _.closeDate.get })

    val dates = (0 to 7).reverse map { v =>
      reportDateFormat.format(daysAgo(v))
    }

    dates map { d =>
      Array(d, openStats.getOrElse(d, 0), closedStats.getOrElse(d, 0))
    }
  }

  private def getReviewStatsByDate(reviews: Seq[Review], df: (Review) => Date, num: Int = 7): Map[String, Int] = {
    val a = reviews.map(df).map(reportDateFormat.format(_)).groupBy { r => r }

    (a.map {
      case (date, num) =>
        (date -> num.size)
    })
  }

  private def getLeaderBoard(
    openReviews: Seq[ReviewDetails],
    recentClosedReviews: Seq[ReviewDetails],
    recentOpenReviews: Seq[ReviewDetails],
    num: Int = 5): (Seq[ReviewLeaderUser], Seq[ReviewLeaderUser]) = {
    
    val scoring: Scoring = new CompoundOpenClosedScoring

    val users = client.getUsers.map { u =>
      (u.userName, u.copy(avatarUrl = forceRetroStyle(u.avatarUrl)))
    } toMap
    
    val board = scoring.score(users.values.toSeq, openReviews, recentClosedReviews, recentOpenReviews, num)
    
    def toLeaderUser(r: LeaderBoardRow) = {
      if (users.contains(r.name)) {
        ReviewLeaderUser(r.name, users(r.name).avatarUrl, r.score)
      } else {
        ReviewLeaderUser(r.name, missingAvatar, r.score)
      }
    }

    (board.rankedWinners.map(toLeaderUser),
        board.rankedLosers.map(toLeaderUser))
  }
  
  val missingAvatar = "http://gravatar.com/avatar/00000000000000000000000000000000?d=retro&s=48"
  
  val defaultAvatar="""(.+d=)([^&]+)(.*)""".r
  def forceRetroStyle(avatarUrl: String) = {
    avatarUrl match {
      case defaultAvatar(url, template, otherParams) => s"${url}retro${otherParams}"
      case _ => avatarUrl
    }
  }
}

case class ReviewLeaderUser(
  name: String,
  avatarUrl: String,
  score: String
)  

case class ReviewLeaderStats(
  fame: Seq[ReviewLeaderUser],
  shame: Seq[ReviewLeaderUser],
  totalOpenReviews: Int,
  updateDate: String,
  openCloseStats: Seq[Array[Any]],
  openCountStats: Seq[Array[Any]]
)
