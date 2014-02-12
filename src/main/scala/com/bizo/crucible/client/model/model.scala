package com.bizo.crucible.client.model

import java.util.Date


case class SearchResponse(reviewData: Seq[ReviewSummary])
case class ReviewSummary(permaId: Id, createDate: Date, closeDate: Option[Date])
case class Id(id: String)

case class ReviewResponse(reviewer: Seq[ReviewerState])
case class ReviewerState(
  userName: String,
  completed: Boolean
)

case class User(
  userName: String,
  displayName: String,
  avatarUrl: String
)