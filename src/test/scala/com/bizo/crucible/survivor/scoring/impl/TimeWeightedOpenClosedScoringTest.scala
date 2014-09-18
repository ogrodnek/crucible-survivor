package com.bizo.crucible.survivor.scoring.impl

import scala.concurrent.duration._
import org.scalatest._
import org.joda.time._

class TimeWeightedOpenClosedScoringTest extends WordSpec with Matchers {

  import TimeWeightedOpenClosedScoring._

  val tz = DateTimeZone.forID("PST8PDT")

  "The work hour penalty calculator" should {
    "Count all time within a single work day" in {
      val start = DateTime.parse("2014-09-08T10:00:00-07:00")
      val end = DateTime.parse("2014-09-08T16:00:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(end.getMillis - start.getMillis)
    }

    "Count no time overnight" in {
      val start = DateTime.parse("2014-09-08T20:00:00-07:00")
      val end = DateTime.parse("2014-09-09T08:00:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.TUESDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(0.0)
    }

    "Count the work hours for an interval including both work and non-work hours" in {
      val start = DateTime.parse("2014-09-08T05:00:00-07:00")
      val end = DateTime.parse("2014-09-08T15:00:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(6.hours.toMillis)
    }

    "Count 8 hours per full weekday" in {
      val start = DateTime.parse("2014-09-08T00:00:00-07:00")
      val end = start.plusDays(3)

      // assert inputs are weekdays
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.THURSDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(3 * 8.hours.toMillis)
    }

    "Not count time on weekends" in {
      val start = DateTime.parse("2014-09-06T00:00:00-07:00")
      val end = DateTime.parse("2014-09-07T00:00:00-07:00")

      // assert inputs are weekends
      start.getDayOfWeek should be(DateTimeConstants.SATURDAY)
      end.getDayOfWeek should be(DateTimeConstants.SUNDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(0.0)
    }

    "Count weekdays in intervals spanning both weekends and weekdays" in {
      val start = DateTime.parse("2014-09-07T05:00:00-07:00")
      val end = DateTime.parse("2014-09-08T15:00:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.SUNDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(6.hours.toMillis)
    }

    "Include partial hours at the beginning, during work hours" in {
      val start = DateTime.parse("2014-09-08T9:30:00-07:00")
      val end = DateTime.parse("2014-09-08T16:00:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(end.getMillis - start.getMillis)
    }

    "Include partial hours at the end, during work horus" in {
      val start = DateTime.parse("2014-09-08T10:00:00-07:00")
      val end = DateTime.parse("2014-09-08T16:30:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(end.getMillis - start.getMillis)
    }

    "Not include partial hours at the beginning, outside of work horus" in {
      val start = DateTime.parse("2014-09-08T8:45:00-07:00")
      val end = DateTime.parse("2014-09-08T16:00:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(7.hours.toMillis)
    }

    "Not include partial hours at the end, outside of work hours" in {
      val start = DateTime.parse("2014-09-08T10:00:00-07:00")
      val end = DateTime.parse("2014-09-08T17:30:00-07:00")

      // assert inputs are a weekday
      start.getDayOfWeek should be(DateTimeConstants.MONDAY)
      end.getDayOfWeek should be(DateTimeConstants.MONDAY)

      new WorkHourPenaltyCalculator(tz).apply(Start(start.getMillis), End(end.getMillis)) should be(7.hours.toMillis)
    }
  }

}
