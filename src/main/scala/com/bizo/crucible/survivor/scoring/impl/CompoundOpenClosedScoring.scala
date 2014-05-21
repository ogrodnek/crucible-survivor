package com.bizo.crucible.survivor.scoring.impl

import com.bizo.crucible.client.model._
import com.bizo.crucible.survivor.scoring._


/**
 * Score by total open reviews ascending, with secondary sort of recent completed reviews.
 * 
 * Users with most open reviews will be on shame
 * 
 * Winners will be least open reviews, using # recently completed as tie-breaker.
 * 
 * Score will be shown as "${numOpen}/${numRecentlyCompleted}"
 */
class CompoundOpenClosedScoring extends Scoring {
  override def score(activeUsers: Seq[User],
      openReviews: Seq[ReviewResponse],
      recentClosedReviews: Seq[ReviewResponse],
      recentOpenReviews: Seq[ReviewResponse],
      num: Int): LeaderBoard = {

    val users = activeUsers.map(u => (u.userName -> u)).toMap

    val recentReviews = (recentClosedReviews ++ recentOpenReviews).flatMap(_.reviewer).groupBy(_.userName)
    val recentCompletedReviewCountByUser = recentReviews.map {
      case (reviewer, states) =>
        reviewer -> states.foldLeft(0) { (sum, state) => if (state.completed) sum + 1 else sum }
    }

    val allOpenReviewsByUser = openReviews.flatMap(_.reviewer).groupBy(_.userName)
    val openReviewCountByUser = allOpenReviewsByUser.map {
      case (reviewer, states) =>
        reviewer -> states.foldLeft(0) { (sum, state) => if (!state.completed) sum + 1 else sum }
    }

    val leaderboard: Seq[ScoredUser] = openReviewCountByUser.map {
      case (user, numOpen) =>
        val numComplete = recentCompletedReviewCountByUser.getOrElse(user, 0)
        
        ScoredUser(user, numOpen, numComplete)
    }.toVector
      .sortBy(_.completeReviews).reverse // completed reviews (secondary sort)
      .sortBy(_.openReviews) // open reviews (primary sort)
      .filter(user => users.contains(user.name) || user.openReviews > 0) // remove deleted users w/0 open

    val winners = leaderboard.take(5).map(_.toLeaderBoardRow)
    val losers = leaderboard.takeRight(5).reverse.map(_.toLeaderBoardRow)

    LeaderBoard(winners, losers)
  }
  
  case class ScoredUser(name: String, openReviews: Int, completeReviews: Int) {
    def toLeaderBoardRow() = {
      LeaderBoardRow(name, s"${openReviews}/${completeReviews}")
    }
  }
}