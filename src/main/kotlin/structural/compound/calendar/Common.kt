package structural.compound.calendar

// 캘린더 이벤트 데이터 클래스
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val startDate: String,
    val endDate: String,
    val category: String
)