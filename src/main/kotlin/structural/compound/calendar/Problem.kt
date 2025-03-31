package structural.compound.calendar

import java.time.LocalDate

class Problem {
    // 기존 방식의 모놀리식 캘린더 구현
    class MonolithicCalendar {
        private var currentDate = LocalDate.now()
        private val events = mutableListOf<CalendarEvent>()
        private var viewMode = "월간"

        // 모든 기능이 하나의 클래스에 집중됨

        fun addEvent(event: CalendarEvent) {
            events.add(event)
            // UI 갱신 로직도 함께 포함됨
            refreshUI()
        }

        fun removeEvent(eventId: String) {
            events.removeIf { it.id == eventId }
            refreshUI()
        }

        fun changeViewMode(mode: String) {
            viewMode = mode
            refreshUI()
        }

        fun selectDate(date: LocalDate) {
            currentDate = date
            // 날짜 선택 시 작업
            refreshUI()
        }

        private fun refreshUI() {
            // UI 갱신 로직 - 실제로는 더 복잡함
            println("캘린더 UI 갱신: 현재 모드 $viewMode, 선택된 날짜 $currentDate")
            println("이벤트 수: ${events.size}")
        }

        fun renderCalendar(): String {
            // 캘린더 렌더링 로직
            return when (viewMode) {
                "월간" -> renderMonthView()
                "주간" -> renderWeekView()
                "일간" -> renderDayView()
                else -> "지원되지 않는 뷰 모드"
            }
        }

        private fun renderMonthView(): String {
            // 월간 뷰 렌더링 로직
            return "월간 캘린더 뷰 - $currentDate - 이벤트: ${events.size}개"
        }

        private fun renderWeekView(): String {
            // 주간 뷰 렌더링 로직
            return "주간 캘린더 뷰 - $currentDate - 이벤트: ${events.size}개"
        }

        private fun renderDayView(): String {
            // 일간 뷰 렌더링 로직
            return "일간 캘린더 뷰 - $currentDate - 이벤트: ${events.size}개"
        }
    }
}

fun main() {
    val calendar = Problem.MonolithicCalendar()

    // 테스트 이벤트 추가
    val event1 = CalendarEvent(
        id = "evt1",
        title = "미팅",
        description = "프로젝트 상태 업데이트",
        startDate = "2025-04-01",
        endDate = "2025-04-01",
        category = "업무"
    )
    calendar.addEvent(event1)

    // 뷰 모드 변경
    calendar.changeViewMode("주간")
    calendar.selectDate(LocalDate.now().plusDays(2))

    // 캘린더 렌더링
    val renderedCalendar = calendar.renderCalendar()
    println(renderedCalendar)
}