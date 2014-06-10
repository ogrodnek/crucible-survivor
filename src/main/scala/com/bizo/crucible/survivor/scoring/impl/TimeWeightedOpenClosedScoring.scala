package com.bizo.crucible.survivor.scoring.impl

import scala.concurrent.duration._

import com.bizo.crucible.survivor.scoring._
import com.bizo.crucible.client.model._

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
class TimeWeightedOpenClosedScoring(now: () => Long = System.currentTimeMillis) extends Scoring {

  def score(
    users: Seq[User],
    openReviews: Seq[ReviewDetails],
    recentClosedReviews: Seq[ReviewDetails],
    recentOpenReviews: Seq[ReviewDetails],
    num: Int): LeaderBoard = {

    val currentTime = now()
    val oneWeekAgo = currentTime - 7.days.toMillis

    // How many milliseconds to penalize a reviewer for the given review
    def openMs(reviewer: ReviewerState, review: Review): Double = {
      reviewer.completionStatusChangeDate.getOrElse(currentTime) - review.createDate.getTime
    }

    def fameScore(reviews: Seq[(ReviewerState, ReviewDetails)]): Double = {
      val penalties = reviews.filter {
        case (reviewer, _) =>
          reviewer.completionStatusChangeDate.map(_ > oneWeekAgo).getOrElse(true)
      }.map {
        case (reviewer, review) =>
          openMs(reviewer, review)
      }

      val avgMillis = penalties.sum / penalties.size

      avgMillis / 1.hour.toMillis
    }

    def shameScore(reviews: Seq[(ReviewerState, ReviewDetails)]): Int = {
      val penalties = reviews.filter {
        case (reviewer, review) =>
          review.closeDate.isEmpty && !reviewer.completed
      }.map {
        case (reviewer, review) =>
          openMs(reviewer, review)
      }

      val maxMillis = if (penalties.isEmpty) 0.0 else penalties.max
      (maxMillis / 1.day.toMillis).toInt
    }

    val scoredReviewers = (openReviews ++ recentClosedReviews).flatMap { review =>
      review.reviewers.map(_ -> review)
    }.groupBy(_._1.userName).map {
      case (reviewerName, reviews) =>
        val mostRecentCompletionTime = reviews.flatMap(_._1.completionStatusChangeDate).max
        
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
        shameScore > 0      
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
