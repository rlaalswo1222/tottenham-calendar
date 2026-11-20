package dev.coys.calendar.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All user-facing dates in the phone's time zone (KST in practice), Korean labels. */
object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
    private val dateFmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)
    private val shortDateFmt = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
    private val fullDateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN)
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

    fun localDate(i: Instant): LocalDate = i.atZone(zone).toLocalDate()
    fun time(i: Instant): String = timeFmt.format(i.atZone(zone))
    fun date(i: Instant): String = dateFmt.format(i.atZone(zone))
    fun dateTime(i: Instant): String = dateTimeFmt.format(i.atZone(zone))
    fun shortDate(i: Instant): String = shortDateFmt.format(i.atZone(zone))
    fun fullDate(i: Instant): String = fullDateFmt.format(i.atZone(zone))
    fun month(m: YearMonth): String = monthFmt.format(m)

    val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

    /** Sunday-first offset of the 1st of the month (0..6). */
    fun leadingBlanks(m: YearMonth): Int = m.atDay(1).dayOfWeek.value % 7
}
