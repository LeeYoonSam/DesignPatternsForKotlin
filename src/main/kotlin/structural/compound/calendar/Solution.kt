package structural.compound.calendar

import java.time.LocalDate
import kotlin.properties.Delegates

class Solution {
    // Observer 패턴 관련 인터페이스
    interface CalendarObserver {
        fun update(eventContext: EventContext)
    }

    // 이벤트 컨텍스트 - 변경된 상태 정보를 담는 클래스
    data class EventContext(
        val source: Any,
        val eventType: EventType,
        val data: Any? = null
    )

    // 이벤트 유형
    enum class EventType {
        DATE_SELECTED,
        VIEW_CHANGED,
        EVENT_ADDED,
        EVENT_REMOVED
    }

    // 추상 캘린더 컴포넌트
    abstract class CalendarComponent {
        private val observers = mutableListOf<CalendarObserver>()

        fun addObserver(observer: CalendarObserver) {
            observers.add(observer)
        }

        fun removeObserver(observer: CalendarObserver) {
            observers.remove(observer)
        }

        protected fun notifyObservers(eventContext: EventContext) {
            observers.forEach { it.update(eventContext) }
        }

        // 컴포넌트 생명주기 메서드
        abstract fun initialize()
        abstract fun render(): String
        abstract fun cleanup()
    }

    // 캘린더 뷰 전략 인터페이스
    interface CalendarViewStrategy {
        fun createView(model: CalendarModel): String
    }

    // 캘린더 모델 - Observer 패턴 적용
    class CalendarModel {
        var currentDate: LocalDate by Delegates.observable(LocalDate.now()) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                notifyObservers(EventContext(this, EventType.DATE_SELECTED, newValue))
            }
        }

        private val _events = mutableListOf<CalendarEvent>()
        val events: List<CalendarEvent> get() = _events.toList()

        private val observers = mutableListOf<CalendarObserver>()

        fun addObserver(observer: CalendarObserver) {
            observers.add(observer)
        }

        fun removeObserver(observer: CalendarObserver) {
            observers.remove(observer)
        }

        private fun notifyObservers(eventContext: EventContext) {
            observers.forEach { it.update(eventContext) }
        }

        fun addEvent(event: CalendarEvent) {
            _events.add(event)
            notifyObservers(EventContext(this, EventType.EVENT_ADDED, event))
        }

        fun removeEvent(eventId: String) {
            val event = _events.find { it.id == eventId }
            if (event != null && _events.removeIf { it.id == eventId }) {
                notifyObservers(EventContext(this, EventType.EVENT_REMOVED, event))
            }
        }

        fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
            return events.filter {
                val startDate = LocalDate.parse(it.startDate)
                val endDate = LocalDate.parse(it.endDate)
                (date.isEqual(startDate) || date.isAfter(startDate)) && (date.isEqual(endDate) || date.isBefore(endDate))
            }
        }
    }

    // 뷰 전략 구현 - Strategy 패턴 적용
    class MonthViewStrategy : CalendarViewStrategy {
        override fun createView(model: CalendarModel): String {
            val date = model.currentDate
            val events = model.events
            return """
                |=== 월간 캘린더 (${date.month} ${date.year}) ===
                |현재 선택된 날짜: ${date.dayOfMonth}
                |이벤트 수: ${events.size}
                |${renderMonthGrid(model)}
            """.trimIndent()
        }

        private fun renderMonthGrid(model: CalendarModel): String {
            // 실제 구현에서는 한 달의 모든 날짜를 그리드로 표시
            return "월간 그리드 (표시 공간 절약을 위해 간략화)"
        }
    }

    class WeekViewStrategy : CalendarViewStrategy {
        override fun createView(model: CalendarModel): String {
            val date = model.currentDate
            val weekStart = date.minusDays(date.dayOfWeek.value - 1L)
            val events = model.events
            return """
            |=== 주간 캘린더 ($weekStart - ${weekStart.plusDays(6)}) ===
            |현재 선택된 날짜: $date
            |이벤트 수: ${events.size}
            |${renderWeekTimeline(model)}
        """.trimMargin()
        }

        private fun renderWeekTimeline(model: CalendarModel): String {
            // 실제 구현에서는 한 주의 모든 날짜를 타임라인으로 표시
            return "주간 타임라인 (표시 공간 절약을 위해 간략화)"
        }
    }

    class DayViewStrategy : CalendarViewStrategy {
        override fun createView(model: CalendarModel): String {
            val date = model.currentDate
            val events = model.getEventsForDate(date)
            return """
            |=== 일간 캘린더 (${date}) ===
            |이벤트 수: ${events.size}
            |${renderDaySchedule(date, events)}
        """.trimMargin()
        }

        private fun renderDaySchedule(date: LocalDate, events: List<CalendarEvent>): String {
            val schedule = StringBuilder()
            schedule.append("일간 일정:\n")

            if (events.isEmpty()) {
                schedule.append("  - 예정된 일정이 없습니다.")
            } else {
                events.forEach {
                    schedule.append("  - ${it.title} (${it.category})\n")
                    schedule.append("    ${it.description}\n")
                }
            }

            return schedule.toString()
        }
    }

    // 캘린더 뷰 컨트롤러 - Composite 패턴 적용
    class CalendarViewController(private val model: CalendarModel) : CalendarComponent(), CalendarObserver {
        private var viewStrategy: CalendarViewStrategy = MonthViewStrategy()

        // 하위 컴포넌트들
        private val headerComponent = CalendarHeaderComponent(model)
        private val toolbarComponent = CalendarToolbarComponent(model)
        private val contentComponent = CalendarContentComponent(model)

        init {
            // 자신을 모델의 옵저버로 등록
            model.addObserver(this)

            // 하위 컴포넌트들을 자신의 옵저버로 등록
            addObserver(headerComponent)
            addObserver(toolbarComponent)
            addObserver(contentComponent)
        }

        override fun initialize() {
            headerComponent.initialize()
            toolbarComponent.initialize()
            contentComponent.initialize()
        }

        override fun render(): String {
            val sb = StringBuilder()
            sb.appendLine(headerComponent.render())
            sb.appendLine(toolbarComponent.render())
            sb.appendLine(contentComponent.render())
            sb.appendLine(viewStrategy.createView(model))
            return sb.toString()
        }

        override fun cleanup() {
            headerComponent.cleanup()
            toolbarComponent.cleanup()
            contentComponent.cleanup()
            model.removeObserver(this)
        }

        override fun update(eventContext: EventContext) {
            // 모델 변경사항을 받아 하위 컴포넌트에 전파
            notifyObservers(eventContext)
        }

        // 뷰 전략 변경 - Strategy 패턴 적용
        fun setViewStrategy(strategy: CalendarViewStrategy) {
            viewStrategy = strategy
            notifyObservers(EventContext(this, EventType.VIEW_CHANGED, strategy))
        }

        // 사용자 액션 처리 메서드
        fun selectDate(date: LocalDate) {
            model.currentDate = date
        }

        fun addEvent(event: CalendarEvent) {
            model.addEvent(event)
        }

        fun removeEvent(eventId: String) {
            model.removeEvent(eventId)
        }
    }

    // 헤더 컴포넌트
    class CalendarHeaderComponent(private val model: CalendarModel) : CalendarComponent(), CalendarObserver {
        override fun initialize() {
            // 헤더 초기화 로직
        }

        override fun render(): String {
            return "===== 캘린더 애플리케이션 ====="
        }

        override fun cleanup() {
            // 정리 로직
        }

        override fun update(eventContext: EventContext) {
            // 이벤트에 따른 헤더 업데이트 로직
        }
    }

    // 툴바 컴포넌트
    class CalendarToolbarComponent(private val model: CalendarModel) : CalendarComponent(), CalendarObserver {
        override fun initialize() {
            // 툴바 초기화 로직
        }

        override fun render(): String {
            return "[ 일간 보기 | 주간 보기 | 월간 보기 | 이벤트 추가 ]"
        }

        override fun cleanup() {
            // 정리 로직
        }

        override fun update(eventContext: EventContext) {
            // 이벤트에 따른 툴바 업데이트 로직
        }
    }

    // 콘텐츠 컴포넌트
    class CalendarContentComponent(private val model: CalendarModel) : CalendarComponent(), CalendarObserver {
        override fun initialize() {
            // 콘텐츠 초기화 로직
        }

        override fun render(): String {
            return "현재 선택된 날짜: ${model.currentDate}"
        }

        override fun cleanup() {
            // 정리 로직
        }

        override fun update(eventContext: EventContext) {
            // 이벤트에 따른 콘텐츠 업데이트 로직
            when (eventContext.eventType) {
                EventType.DATE_SELECTED -> {
                    // 날짜 선택 처리
                }
                EventType.EVENT_ADDED -> {
                    // 이벤트 추가 처리
                }
                EventType.EVENT_REMOVED -> {
                    // 이벤트 제거 처리
                }
                else -> {
                    // 기타 이벤트 처리
                }
            }
        }
    }

    // Facade 패턴을 적용한 캘린더 퍼사드
    class CalendarFacade {
        private val model = CalendarModel()
        private val controller = CalendarViewController(model)

        init {
            controller.initialize()
        }

        // 단순화된 인터페이스 제공
        fun renderCalendar(): String {
            return controller.render()
        }

        fun switchToMonthView() {
            controller.setViewStrategy(MonthViewStrategy())
        }

        fun switchToWeekView() {
            controller.setViewStrategy(WeekViewStrategy())
        }

        fun switchToDayView() {
            controller.setViewStrategy(DayViewStrategy())
        }

        fun selectDate(date: LocalDate) {
            controller.selectDate(date)
        }

        fun addEvent(event: CalendarEvent) {
            controller.addEvent(event)
        }

        fun removeEvent(eventId: String) {
            controller.removeEvent(eventId)
        }

        fun cleanup() {
            controller.cleanup()
        }
    }
}

fun main() {
    // Compound 패턴을 사용한 캘린더 애플리케이션
    val calendar = Solution.CalendarFacade()

    // 이벤트 추가
    val event1 = CalendarEvent(
        id = "evt1",
        title = "프로젝트 미팅",
        description = "분기별 프로젝트 상태 업데이트",
        startDate = "2025-04-01",
        endDate = "2025-04-01",
        category = "업무"
    )
    calendar.addEvent(event1)

    // 현재 월간 뷰로 캘린더 렌더링
    println(calendar.renderCalendar())
    println("\n")

    // 주간 뷰로 전환
    calendar.switchToWeekView()
    println(calendar.renderCalendar())
    println("\n")

    // 일간 뷰로 전환 및 날짜 선택
    calendar.switchToDayView()
    calendar.selectDate(LocalDate.of(2025, 4, 1))
    println(calendar.renderCalendar())

    // 애플리케이션 종료 시 정리
    calendar.cleanup()
}