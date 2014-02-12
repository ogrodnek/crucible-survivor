package com.bizo.crucible.client.ser

import java.util.Date
import javax.ws.rs.ext.Provider
import java.lang.reflect.Type
import javax.ws.rs.core.MediaType
import net.liftweb.json.JsonParser
import net.liftweb.json.DefaultFormats
import java.text.SimpleDateFormat
import com.bizo.crucible.client.model._


object ReviewSearchResponseParser {
  implicit val formats = new DefaultFormats {
    // 2011-09-01T15:49:30.746-0700
   override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }
  
  def parse(in: String): Seq[ReviewSummary] = {
    val json = JsonParser.parse(in)
    
    val r = json.extract[SearchResponse]
    
    r.reviewData
  }
}

object ReviewResponseParser {
  implicit val formats = new DefaultFormats { }
  
  def parse(in: String): ReviewResponse = {
    val json = (JsonParser.parse(in) \ "reviewers")
    json.extract[ReviewResponse]
  }
}

object UserResponseParser {
  implicit val formats = new DefaultFormats { }
  
  def parse(in: String): Seq[User] = {
    val json = (JsonParser.parse(in) \ "userData")
    json.extract[List[User]]
  }
}