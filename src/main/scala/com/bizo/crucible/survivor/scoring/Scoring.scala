package com.bizo.crucible.survivor.scoring

import com.bizo.crucible.client.model._

case class LeaderBoardRow(
  name: String,
  score: String
)

case class LeaderBoard(
  rankedWinners: Seq[LeaderBoardRow],
  rankedLosers: Seq[LeaderBoardRow]
)  

trait Scoring {
  def score(users: Seq[User],
      openReviews: Seq[ReviewDetails],
      recentClosedReviews: Seq[ReviewDetails],
      recentOpenReviews: Seq[ReviewDetails],
      num: Int): LeaderBoard
}