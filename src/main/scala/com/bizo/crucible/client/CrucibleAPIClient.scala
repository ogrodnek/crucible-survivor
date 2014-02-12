package com.bizo.crucible.client

import java.io.FileInputStream
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.DefaultClientConfig
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter
import org.apache.commons.io.IOUtils
import com.bizo.crucible.client.ser._
import com.bizo.crucible.client.model._
import java.io.OutputStreamWriter
import java.io.FileOutputStream

class CrucibleAPIClient(host: String, creds: CredentialsProvider) extends CrucibleAPI {
  val client = Client.create(config)
  
  def getReviewsInState(state: String*): Seq[ReviewSummary] = {
    val states = state.mkString(",")
    
    val r = resource("/reviews-v1").queryParam("state", states).accept("application/json")
    
    val reviews = r.get(classOf[String])
    
    ReviewSearchResponseParser.parse(reviews)
  }
  
  def getUsers(): Map[String, User] = {
    val r = resource(s"/users-v1").accept("application/json")
    val users = UserResponseParser.parse(r.get(classOf[String]))
    
    users.map{ u => (u.userName, u) } toMap
  }
  
  def getReview(id: Id): ReviewResponse  = {
    val r = resource("/reviews-v1/%s/details".format(id.id)).accept("application/json")
    
    val ret = ReviewResponseParser.parse(r.get(classOf[String]))
    
    ret
  } 
  
  private def resource(path: String) = {
    val r = client.resource("https://%s/rest-service%s".format(host, path))
    r.addFilter(new HTTPBasicAuthFilter(creds.user, creds.password))
    
    r
  }

  private def config() = {
    val c = new DefaultClientConfig

    c
  }  
}