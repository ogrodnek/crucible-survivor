package com.bizo.crucible.client

import com.bizo.crucible.client.model._

trait CrucibleAPI {
  def getReviewsInState(state: String*): Seq[ReviewSummary]
  def getReview(id: Id): ReviewResponse
  def getUsers(): Map[String, User]
}
