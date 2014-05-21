package com.bizo.crucible.survivor

import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MILLISECOND
import java.util.Calendar.MINUTE
import java.util.Calendar.SECOND
import java.util.Date
import java.util.Calendar

object DateUtils {
  def daysAgo(numDays: Int): Date = {
    val cal = Calendar.getInstance
    cal.add(Calendar.DAY_OF_YEAR, -(numDays))
    cal.getTime
  }
  
  def monthsAgo(numMonths: Int): Date = {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, -(numMonths))
    c.getTime
  }
  
  def getDaysUntilToday(d: Date): List[Date] = {
    getDaysUntil(d, new java.util.Date())
  }

  def getDaysUntil(_d: Date, _end: Date): List[Date] = {
    val end = clearTimeComponents(_end)
    val d = clearTimeComponents(_d)

    if (d.compareTo(end) <= 0) {
      val next = Calendar.getInstance
      next.setTime(d)
      next.add(Calendar.DAY_OF_YEAR, 1)

      d :: getDaysUntil(next.getTime, end)
    } else {
      List()
    }
  }
  
  private def clearTimeComponents(d: Date): Date = {
    import Calendar._
    val c = Calendar.getInstance()
    c.setTime(d)

    for (f <- Seq(HOUR_OF_DAY, MINUTE, SECOND, MILLISECOND)) {
      c.set(f, 0)
    }

    c.getTime
  }  
}