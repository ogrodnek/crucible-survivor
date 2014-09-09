package com.bizo.crucible.survivor.scoring.impl

import scala.concurrent.duration._

import com.bizo.crucible.survivor.scoring._
import com.bizo.crucible.client.model._
import org.joda.time._

/**
 * This scoring algorithm ranks people by how long each review has been open.
 *
 * The shame board is sorted based on the longest open review and is measured in days.  The fame board is sorted
 * according to the average open time for all non-completed reviews and all completed reviews in the past week.  Fame
 * is measured in hours.  (It's still longer than 15 minutes in most cases!)
 *
 * In both cases, ties are broken based on the time of the most recent review being completed with more recent
 * completion being better.
 */
object TimeWeightedOpenClosedScoring {
  import DateTimeConstants._

  private def toTopOfHour(dt: DateTime): DateTime = {
    dt.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
  }

  trait PenaltyCalculator {
    // How many MS to penalize a reviewer
    def apply(start: Long, end: Long): Long

    // maximum penalty for any one day, used to determine what "1d" means on the shame board
    def maxPenaltyMsPerDay: Long = 1.day.toMillis
  }

  // Penalize all the times! -- here for reference
  object NaivePenaltyCalculator extends PenaltyCalculator {
    override def apply(start: Long, end: Long): Long = (end - start)
  }

  // gets a work hour penalty calculator with that only counts work hours 9-5 M-F in the given time zone
  class WorkHourPenaltyCalculator(tz: DateTimeZone) extends PenaltyCalculator {
    override def apply(start: Long, end: Long): Long = {
      val startDt = new DateTime(start, tz)
      val endDt = new DateTime(end, tz)

      val startAtTopOfHour = toTopOfHour(startDt)
      val endAtTopOfHour = toTopOfHour(endDt)

      val hoursOpen = Iterator.iterate(startAtTopOfHour)(_.plusHours(1)).takeWhile(_.isBefore(endAtTopOfHour)).toSeq
      val wholePenaltyMs = hoursOpen.filter(isWorkHour).size * 1.hour.toMillis

      val partialPenaltyMs = if (isWorkHour(endDt)) {
        endDt.getMillis - endAtTopOfHour.getMillis
      } else {
        0
      }

      wholePenaltyMs + partialPenaltyMs
    }

    override def maxPenaltyMsPerDay: Long = 8.hours.toMillis
  }

  // 9-5, M-F in the input's time zone
  private def isWorkHour(dt: DateTime): Boolean = {
    val isWorkDay = Set(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).contains(dt.getDayOfWeek)

    val workDayStart = toTopOfHour(dt.withHourOfDay(9))
    val workDayEnd = toTopOfHour(dt.withHourOfDay(17))

    isWorkDay && (workDayStart == dt || workDayStart.isBefore(dt)) && dt.isBefore(workDayEnd)
  }
}

class TimeWeightedOpenClosedScoring(
  now: () => Long = System.currentTimeMillis) extends Scoring {

  import TimeWeightedOpenClosedScoring._
  private val penaltyCalculator: PenaltyCalculator = new WorkHourPenaltyCalculator(DateTimeZone.forID("PST8PDT"))

  def score(
    users: Seq[User],
    openReviews: Seq[ReviewDetails],
    recentClosedReviews: Seq[ReviewDetails],
    recentOpenReviews: Seq[ReviewDetails],
    num: Int): LeaderBoard = {

    val currentTime = now()
    val oneWeekAgo = currentTime - 7.days.toMillis

    // How many milliseconds to penalize a reviewer for the given review
    def penaltyMs(reviewer: ReviewerState, review: Review): Long = {
      val rawStartMs = review.createDate.getTime
      val rawEndMs = reviewer.completionStatusChangeDate.getOrElse(currentTime)
      penaltyCalculator(rawEndMs, rawStartMs)
    }

    // measured in hours
    def fameScore(reviews: Seq[(ReviewerState, ReviewDetails)]): Double = {
      val penalties = reviews.filter {
        case (reviewer, _) =>
          reviewer.completionStatusChangeDate.map(_ > oneWeekAgo).getOrElse(true)
      }.map {
        case (reviewer, review) =>
          penaltyMs(reviewer, review)
      }

      val avgMillis = penalties.sum.toDouble / penalties.size

      avgMillis / 1.hour.toMillis
    }

    // measured in "days"
    def shameScore(reviews: Seq[(ReviewerState, ReviewDetails)]): Int = {
      val penalties = reviews.filter {
        case (reviewer, review) =>
          review.closeDate.isEmpty && !reviewer.completed
      }.map {
        case (reviewer, review) =>
          penaltyMs(reviewer, review)
      }

      val maxMillis = if (penalties.isEmpty) 0L else penalties.max
      (maxMillis.toDouble / penaltyCalculator.maxPenaltyMsPerDay).toInt
    }

    val scoredReviewers = (openReviews ++ recentClosedReviews).flatMap { review =>
      review.reviewers.map(_ -> review)
    }.groupBy(_._1.userName).map {
      case (reviewerName, reviews) =>
        val mostRecentCompletionTime = reviews.map {
          case (state, _) =>
            state.completionStatusChangeDate.getOrElse(0L)
        }.max

        (reviewerName, fameScore(reviews), shameScore(reviews), mostRecentCompletionTime)
    }.toIndexedSeq

    val fameBoard = scoredReviewers.sortBy {
      case (_, fameScore, _, mostRecentCompletionTime) =>
        (fameScore, currentTime - mostRecentCompletionTime)
    }.take(num).map {
      case (reviewerName, fameScore, _, _) =>
        LeaderBoardRow(reviewerName, formatHours(fameScore))
    }

    val shameBoard = scoredReviewers.filter {
      case (_, _, shameScore, mostRecentCompletionTime) =>
        shameScore > 2
    }.sortBy {
      case (_, _, shameScore, mostRecentCompletionTime) =>
        (-shameScore, mostRecentCompletionTime)
    }.take(num).map {
      case (reviewerName, _, shameScore, _) =>
        LeaderBoardRow(reviewerName, s"${shameScore}d")
    }

    LeaderBoard(fameBoard, shameBoard)
  }

  private[this] def formatHours(hours: Double): String = {
    val wholeHours = hours.toInt
    val minutes = (hours - wholeHours) * 1.hour.toMinutes

    f"${wholeHours}h${minutes}%02.0fm"
  }
}
