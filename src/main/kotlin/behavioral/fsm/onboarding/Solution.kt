package behavioral.fsm.onboarding

/**
 * Finite State Machine (FSM) Pattern - Solution
 *
 * FSM은 시스템이 가질 수 있는 모든 상태와 상태 간 전이를 명시적으로 정의하는 패턴입니다.
 *
 * 핵심 개념:
 * - State(상태): 시스템이 있을 수 있는 유한한 상태 집합
 * - Event(이벤트): 상태 전이를 유발하는 입력
 * - Transition(전이): 현재 상태 + 이벤트 → 다음 상태의 규칙
 * - Guard(가드): 전이가 허용되는 조건
 * - Action(액션): 전이 시 실행되는 부수효과
 *
 * 예시: 앱 온보딩 플로우
 */

import java.time.LocalDateTime

// ========================================
// 1. 타입 안전한 상태/이벤트 정의
// ========================================

/**
 * 온보딩의 모든 가능한 상태
 *
 * sealed class로 정의하여:
 * - 유한한 상태 집합이 명확
 * - 각 상태가 필요한 데이터를 캡슐화
 * - when에서 exhaustive check 보장
 */
sealed class OnboardingState {
    /** 약관 동의 */
    object TermsAgreement : OnboardingState() {
        override fun toString() = "TermsAgreement"
    }

    /** 보호자 동의 (미성년자 전용) */
    data class ParentalConsent(val isUnder14: Boolean) : OnboardingState()

    /** 프로필 설정 */
    object ProfileSetup : OnboardingState() {
        override fun toString() = "ProfileSetup"
    }

    /** 이메일 인증 */
    data class EmailVerification(val email: String) : OnboardingState()

    /** 관심사 선택 */
    object InterestSelection : OnboardingState() {
        override fun toString() = "InterestSelection"
    }

    /** 알림 권한 */
    object NotificationPermission : OnboardingState() {
        override fun toString() = "NotificationPermission"
    }

    /** 완료 */
    data class Completed(val userId: String) : OnboardingState()

    /** 에러 (복구 가능) */
    data class Error(val previousState: OnboardingState, val message: String) : OnboardingState()
}

/**
 * 온보딩에서 발생할 수 있는 모든 이벤트
 */
sealed class OnboardingEvent {
    data class AcceptTerms(val isUnder14: Boolean = false) : OnboardingEvent()
    object DeclineTerms : OnboardingEvent()
    data class ParentalConsentGranted(val guardianName: String) : OnboardingEvent()
    data class CompleteProfile(val name: String, val email: String, val age: Int) : OnboardingEvent()
    data class VerifyEmail(val code: String) : OnboardingEvent()
    object ResendVerification : OnboardingEvent()
    data class SelectInterests(val interests: List<String>) : OnboardingEvent()
    data class HandleNotification(val granted: Boolean) : OnboardingEvent()
    object Skip : OnboardingEvent()
    object GoBack : OnboardingEvent()
    object RetryFromError : OnboardingEvent()
}

/**
 * 상태 전이 결과
 */
sealed class TransitionResult {
    data class Success(
        val newState: OnboardingState,
        val actions: List<Action> = emptyList()
    ) : TransitionResult()

    data class Denied(val reason: String) : TransitionResult()
}

/**
 * 전이 시 실행할 액션(부수효과)
 */
sealed class Action {
    data class SendVerificationEmail(val email: String) : Action()
    data class SaveProfile(val name: String, val email: String) : Action()
    data class SaveInterests(val interests: List<String>) : Action()
    data class RegisterNotification(val enabled: Boolean) : Action()
    data class CreateUser(val userId: String) : Action()
    data class LogTransition(val from: OnboardingState, val to: OnboardingState, val event: OnboardingEvent) : Action()
    data class ShowError(val message: String) : Action()
}

// ========================================
// 2. FSM 엔진 (범용)
// ========================================

/**
 * 범용 Finite State Machine 엔진
 *
 * 상태(S)와 이벤트(E)를 제네릭으로 받아
 * 어떤 도메인에서든 재사용 가능
 */
class StateMachine<S, E>(
    initialState: S,
    private val transitionHandler: (currentState: S, event: E) -> TransitionResult
) {
    var currentState: S = initialState
        private set

    private val listeners = mutableListOf<(S, E, S) -> Unit>()
    private val transitionLog = mutableListOf<TransitionRecord<S, E>>()

    data class TransitionRecord<S, E>(
        val fromState: S,
        val event: E,
        val toState: S,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )

    /**
     * 이벤트 처리: 현재 상태 + 이벤트 → 전이 결과
     */
    fun send(event: E): TransitionResult {
        val result = transitionHandler(currentState, event)

        when (result) {
            is TransitionResult.Success -> {
                val previousState = currentState
                currentState = result.newState as S

                // 전이 로그 기록
                transitionLog.add(TransitionRecord(previousState, event, currentState))

                // 리스너 알림
                listeners.forEach { it(previousState, event, currentState) }

                // 액션 실행
                result.actions.forEach { action ->
                    executeAction(action)
                }
            }
            is TransitionResult.Denied -> {
                println("  [FSM] ⚠️ 전이 거부: ${result.reason}")
            }
        }

        return result
    }

    private fun executeAction(action: Action) {
        when (action) {
            is Action.SendVerificationEmail ->
                println("  [Action] 인증 이메일 발송: ${action.email}")
            is Action.SaveProfile ->
                println("  [Action] 프로필 저장: ${action.name}")
            is Action.SaveInterests ->
                println("  [Action] 관심사 저장: ${action.interests}")
            is Action.RegisterNotification ->
                println("  [Action] 알림 등록: ${action.enabled}")
            is Action.CreateUser ->
                println("  [Action] 사용자 생성: ${action.userId}")
            is Action.LogTransition ->
                println("  [Action] 전이 기록: ${action.from} → ${action.to}")
            is Action.ShowError ->
                println("  [Action] 에러 표시: ${action.message}")
        }
    }

    fun addListener(listener: (from: S, event: E, to: S) -> Unit) {
        listeners.add(listener)
    }

    fun getTransitionHistory(): List<TransitionRecord<S, E>> = transitionLog.toList()

    fun canHandle(event: E): Boolean {
        return transitionHandler(currentState, event) is TransitionResult.Success
    }
}

// ========================================
// 3. 온보딩 FSM 정의 (전이 규칙)
// ========================================

/**
 * 온보딩 전이 규칙을 정의하는 핸들러
 *
 * 모든 상태 전이 규칙이 한 곳에 명시적으로 정의됨
 * → 전체 플로우를 한눈에 파악 가능
 */
object OnboardingTransitions {

    fun handle(
        currentState: OnboardingState,
        event: OnboardingEvent
    ): TransitionResult = when (currentState) {

        // --- 약관 동의 ---
        is OnboardingState.TermsAgreement -> when (event) {
            is OnboardingEvent.AcceptTerms -> {
                if (event.isUnder14) {
                    TransitionResult.Success(
                        newState = OnboardingState.ParentalConsent(true),
                        actions = listOf(
                            Action.LogTransition(currentState, OnboardingState.ParentalConsent(true), event)
                        )
                    )
                } else {
                    TransitionResult.Success(
                        newState = OnboardingState.ProfileSetup,
                        actions = listOf(
                            Action.LogTransition(currentState, OnboardingState.ProfileSetup, event)
                        )
                    )
                }
            }
            is OnboardingEvent.DeclineTerms ->
                TransitionResult.Denied("약관에 동의해야 진행할 수 있습니다")
            else -> TransitionResult.Denied("약관 동의 단계에서 허용되지 않는 이벤트: $event")
        }

        // --- 보호자 동의 ---
        is OnboardingState.ParentalConsent -> when (event) {
            is OnboardingEvent.ParentalConsentGranted ->
                TransitionResult.Success(
                    newState = OnboardingState.ProfileSetup,
                    actions = listOf(Action.LogTransition(currentState, OnboardingState.ProfileSetup, event))
                )
            is OnboardingEvent.GoBack ->
                TransitionResult.Success(newState = OnboardingState.TermsAgreement)
            else -> TransitionResult.Denied("보호자 동의 단계에서 허용되지 않는 이벤트")
        }

        // --- 프로필 설정 ---
        is OnboardingState.ProfileSetup -> when (event) {
            is OnboardingEvent.CompleteProfile -> {
                // Guard: 유효성 검증
                if (event.name.isBlank()) {
                    TransitionResult.Denied("이름을 입력해주세요")
                } else if (event.age < 1 || event.age > 150) {
                    TransitionResult.Denied("유효한 나이를 입력해주세요")
                } else {
                    TransitionResult.Success(
                        newState = OnboardingState.EmailVerification(event.email),
                        actions = listOf(
                            Action.SaveProfile(event.name, event.email),
                            Action.SendVerificationEmail(event.email),
                            Action.LogTransition(currentState, OnboardingState.EmailVerification(event.email), event)
                        )
                    )
                }
            }
            is OnboardingEvent.GoBack ->
                TransitionResult.Success(newState = OnboardingState.TermsAgreement)
            else -> TransitionResult.Denied("프로필 설정 단계에서 허용되지 않는 이벤트")
        }

        // --- 이메일 인증 ---
        is OnboardingState.EmailVerification -> when (event) {
            is OnboardingEvent.VerifyEmail -> {
                if (event.code.length == 6) {
                    TransitionResult.Success(
                        newState = OnboardingState.InterestSelection,
                        actions = listOf(Action.LogTransition(currentState, OnboardingState.InterestSelection, event))
                    )
                } else {
                    TransitionResult.Success(
                        newState = OnboardingState.Error(currentState, "잘못된 인증 코드"),
                        actions = listOf(Action.ShowError("잘못된 인증 코드입니다"))
                    )
                }
            }
            is OnboardingEvent.ResendVerification ->
                TransitionResult.Success(
                    newState = currentState,
                    actions = listOf(Action.SendVerificationEmail(currentState.email))
                )
            is OnboardingEvent.GoBack ->
                TransitionResult.Success(newState = OnboardingState.ProfileSetup)
            else -> TransitionResult.Denied("이메일 인증 단계에서 허용되지 않는 이벤트")
        }

        // --- 관심사 선택 ---
        is OnboardingState.InterestSelection -> when (event) {
            is OnboardingEvent.SelectInterests -> {
                if (event.interests.isEmpty()) {
                    TransitionResult.Denied("최소 1개 이상의 관심사를 선택해주세요")
                } else {
                    TransitionResult.Success(
                        newState = OnboardingState.NotificationPermission,
                        actions = listOf(
                            Action.SaveInterests(event.interests),
                            Action.LogTransition(currentState, OnboardingState.NotificationPermission, event)
                        )
                    )
                }
            }
            is OnboardingEvent.Skip ->
                TransitionResult.Success(
                    newState = OnboardingState.NotificationPermission,
                    actions = listOf(Action.LogTransition(currentState, OnboardingState.NotificationPermission, event))
                )
            is OnboardingEvent.GoBack ->
                TransitionResult.Success(newState = OnboardingState.ProfileSetup)
            else -> TransitionResult.Denied("관심사 선택 단계에서 허용되지 않는 이벤트")
        }

        // --- 알림 권한 ---
        is OnboardingState.NotificationPermission -> when (event) {
            is OnboardingEvent.HandleNotification -> {
                val userId = "user-${System.currentTimeMillis() % 10000}"
                TransitionResult.Success(
                    newState = OnboardingState.Completed(userId),
                    actions = listOf(
                        Action.RegisterNotification(event.granted),
                        Action.CreateUser(userId),
                        Action.LogTransition(currentState, OnboardingState.Completed(userId), event)
                    )
                )
            }
            is OnboardingEvent.Skip -> {
                val userId = "user-${System.currentTimeMillis() % 10000}"
                TransitionResult.Success(
                    newState = OnboardingState.Completed(userId),
                    actions = listOf(
                        Action.RegisterNotification(false),
                        Action.CreateUser(userId)
                    )
                )
            }
            is OnboardingEvent.GoBack ->
                TransitionResult.Success(newState = OnboardingState.InterestSelection)
            else -> TransitionResult.Denied("알림 설정 단계에서 허용되지 않는 이벤트")
        }

        // --- 완료 ---
        is OnboardingState.Completed ->
            TransitionResult.Denied("온보딩이 이미 완료되었습니다")

        // --- 에러 (복구) ---
        is OnboardingState.Error -> when (event) {
            is OnboardingEvent.RetryFromError ->
                TransitionResult.Success(
                    newState = currentState.previousState,
                    actions = listOf(Action.LogTransition(currentState, currentState.previousState, event))
                )
            is OnboardingEvent.GoBack ->
                TransitionResult.Success(newState = currentState.previousState)
            else -> TransitionResult.Denied("에러 상태에서는 재시도 또는 뒤로가기만 가능")
        }
    }
}

// ========================================
// 4. 온보딩 매니저 (FSM + UI 연결)
// ========================================

class OnboardingManager {
    val fsm = StateMachine<OnboardingState, OnboardingEvent>(
        initialState = OnboardingState.TermsAgreement,
        transitionHandler = OnboardingTransitions::handle
    )

    // 스킵 가능 여부
    val skippableStates = setOf(
        OnboardingState.InterestSelection::class,
        OnboardingState.NotificationPermission::class
    )

    init {
        // 전이 리스너 등록
        fsm.addListener { from, event, to ->
            println("  [OnboardingManager] $from → $to (by $event)")
        }
    }

    fun currentState(): OnboardingState = fsm.currentState

    fun send(event: OnboardingEvent): TransitionResult {
        println("[Onboarding] 이벤트: $event")
        return fsm.send(event)
    }

    fun canSkip(): Boolean =
        fsm.currentState::class in skippableStates

    fun canGoBack(): Boolean =
        fsm.currentState !is OnboardingState.TermsAgreement &&
        fsm.currentState !is OnboardingState.Completed

    /**
     * 진행률 계산
     */
    fun progress(): Float {
        val totalSteps = 5
        val currentStep = when (fsm.currentState) {
            is OnboardingState.TermsAgreement -> 0
            is OnboardingState.ParentalConsent -> 0
            is OnboardingState.ProfileSetup -> 1
            is OnboardingState.EmailVerification -> 2
            is OnboardingState.InterestSelection -> 3
            is OnboardingState.NotificationPermission -> 4
            is OnboardingState.Completed -> 5
            is OnboardingState.Error -> {
                val prev = (fsm.currentState as OnboardingState.Error).previousState
                return progress(prev, totalSteps)
            }
        }
        return currentStep.toFloat() / totalSteps
    }

    private fun progress(state: OnboardingState, total: Int): Float {
        val step = when (state) {
            is OnboardingState.TermsAgreement -> 0
            is OnboardingState.ProfileSetup -> 1
            is OnboardingState.EmailVerification -> 2
            is OnboardingState.InterestSelection -> 3
            is OnboardingState.NotificationPermission -> 4
            else -> 0
        }
        return step.toFloat() / total
    }

    fun printHistory() {
        println("--- 전이 이력 ---")
        fsm.getTransitionHistory().forEachIndexed { i, record ->
            println("  ${i + 1}. ${record.fromState} → ${record.toState} (${record.event})")
        }
    }
}

// ========================================
// 5. DSL 기반 FSM 빌더 (범용)
// ========================================

/**
 * DSL로 FSM을 선언적으로 정의하는 빌더
 *
 * 사용 예:
 * val fsm = stateMachine<State, Event>(State.Initial) {
 *     state<State.Initial> {
 *         on<Event.Start> { transitionTo(State.Running) }
 *     }
 *     state<State.Running> {
 *         on<Event.Pause> { transitionTo(State.Paused) }
 *         on<Event.Stop> { transitionTo(State.Stopped) }
 *     }
 * }
 */
class StateMachineBuilder<S : Any, E : Any> {
    private val transitionMap = mutableMapOf<String, MutableMap<String, (S, E) -> TransitionResult>>()

    inline fun <reified STATE : S> state(block: StateBuilder<S, E>.() -> Unit) {
        val builder = StateBuilder<S, E>(STATE::class.simpleName ?: "Unknown")
        builder.block()
        transitionMap[builder.stateName] = builder.transitions
    }

    fun build(initialState: S): StateMachine<S, E> {
        return StateMachine(initialState) { current, event ->
            val stateKey = current!!::class.simpleName ?: "Unknown"
            val eventKey = event!!::class.simpleName ?: "Unknown"
            val handler = transitionMap[stateKey]?.get(eventKey)
            handler?.invoke(current, event)
                ?: TransitionResult.Denied("정의되지 않은 전이: $stateKey + $eventKey")
        }
    }

    class StateBuilder<S : Any, E : Any>(val stateName: String) {
        val transitions = mutableMapOf<String, (S, E) -> TransitionResult>()

        inline fun <reified EVENT : E> on(noinline handler: (S, EVENT) -> TransitionResult) {
            val eventKey = EVENT::class.simpleName ?: "Unknown"
            @Suppress("UNCHECKED_CAST")
            transitions[eventKey] = handler as (S, E) -> TransitionResult
        }
    }
}

inline fun <reified S : Any, reified E : Any> stateMachine(
    initialState: S,
    block: StateMachineBuilder<S, E>.() -> Unit
): StateMachine<S, E> {
    val builder = StateMachineBuilder<S, E>()
    builder.block()
    return builder.build(initialState)
}

// ========================================
// 6. DSL 사용 예시: 간단한 미디어 플레이어 FSM
// ========================================

object MediaPlayerExample {

    sealed class PlayerState {
        object Idle : PlayerState() { override fun toString() = "Idle" }
        object Playing : PlayerState() { override fun toString() = "Playing" }
        object Paused : PlayerState() { override fun toString() = "Paused" }
        object Buffering : PlayerState() { override fun toString() = "Buffering" }
        object Stopped : PlayerState() { override fun toString() = "Stopped" }
    }

    sealed class PlayerEvent {
        data class Play(val url: String) : PlayerEvent()
        object Pause : PlayerEvent()
        object Resume : PlayerEvent()
        object Stop : PlayerEvent()
        object BufferComplete : PlayerEvent()
    }

    fun createPlayerFSM(): StateMachine<PlayerState, PlayerEvent> {
        return stateMachine<PlayerState, PlayerEvent>(PlayerState.Idle) {
            state<PlayerState.Idle> {
                on<PlayerEvent.Play> { _, _ ->
                    TransitionResult.Success(PlayerState.Buffering)
                }
            }
            state<PlayerState.Buffering> {
                on<PlayerEvent.BufferComplete> { _, _ ->
                    TransitionResult.Success(PlayerState.Playing)
                }
                on<PlayerEvent.Stop> { _, _ ->
                    TransitionResult.Success(PlayerState.Stopped)
                }
            }
            state<PlayerState.Playing> {
                on<PlayerEvent.Pause> { _, _ ->
                    TransitionResult.Success(PlayerState.Paused)
                }
                on<PlayerEvent.Stop> { _, _ ->
                    TransitionResult.Success(PlayerState.Stopped)
                }
            }
            state<PlayerState.Paused> {
                on<PlayerEvent.Resume> { _, _ ->
                    TransitionResult.Success(PlayerState.Playing)
                }
                on<PlayerEvent.Stop> { _, _ ->
                    TransitionResult.Success(PlayerState.Stopped)
                }
            }
            state<PlayerState.Stopped> {
                on<PlayerEvent.Play> { _, _ ->
                    TransitionResult.Success(PlayerState.Buffering)
                }
            }
        }
    }
}

// ========================================
// Main - 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║        Finite State Machine - 앱 온보딩 플로우 데모           ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    val manager = OnboardingManager()

    // === 정상 플로우 ===
    println("=== 1. 정상 온보딩 플로우 ===")
    println("현재: ${manager.currentState()} (진행률: ${(manager.progress() * 100).toInt()}%)")
    println()

    manager.send(OnboardingEvent.AcceptTerms())
    println("진행률: ${(manager.progress() * 100).toInt()}%")
    println()

    manager.send(OnboardingEvent.CompleteProfile("홍길동", "hong@example.com", 25))
    println("진행률: ${(manager.progress() * 100).toInt()}%")
    println()

    manager.send(OnboardingEvent.VerifyEmail("123456"))
    println("진행률: ${(manager.progress() * 100).toInt()}%")
    println()

    manager.send(OnboardingEvent.SelectInterests(listOf("기술", "음악", "여행")))
    println("진행률: ${(manager.progress() * 100).toInt()}%")
    println()

    manager.send(OnboardingEvent.HandleNotification(true))
    println("진행률: ${(manager.progress() * 100).toInt()}%")
    println()

    // === 전이 이력 ===
    manager.printHistory()
    println()

    // === 유효하지 않은 전이 방지 ===
    println("=== 2. 유효하지 않은 전이 방지 ===")
    val result = manager.send(OnboardingEvent.AcceptTerms())
    println("결과: $result")
    println()

    // === Guard 조건 ===
    println("=== 3. Guard 조건 (유효성 검증) ===")
    val manager2 = OnboardingManager()
    manager2.send(OnboardingEvent.AcceptTerms())
    val guardResult = manager2.send(OnboardingEvent.CompleteProfile("", "test@test.com", 25))
    println("빈 이름 결과: $guardResult")
    println()

    // === 에러 복구 ===
    println("=== 4. 에러 상태와 복구 ===")
    val manager3 = OnboardingManager()
    manager3.send(OnboardingEvent.AcceptTerms())
    manager3.send(OnboardingEvent.CompleteProfile("김철수", "kim@test.com", 30))
    manager3.send(OnboardingEvent.VerifyEmail("wrong")) // 잘못된 코드
    println("현재 상태: ${manager3.currentState()}")
    manager3.send(OnboardingEvent.RetryFromError) // 복구
    println("복구 후: ${manager3.currentState()}")
    println()

    // === 미성년자 분기 ===
    println("=== 5. 조건부 플로우 (미성년자) ===")
    val manager4 = OnboardingManager()
    manager4.send(OnboardingEvent.AcceptTerms(isUnder14 = true))
    println("현재: ${manager4.currentState()}")
    manager4.send(OnboardingEvent.ParentalConsentGranted("보호자"))
    println("보호자 동의 후: ${manager4.currentState()}")
    println()

    // === 스킵 ===
    println("=== 6. 스킵 가능 여부 ===")
    val manager5 = OnboardingManager()
    println("약관 단계 스킵 가능: ${manager5.canSkip()}")
    manager5.send(OnboardingEvent.AcceptTerms())
    manager5.send(OnboardingEvent.CompleteProfile("박지민", "park@test.com", 28))
    manager5.send(OnboardingEvent.VerifyEmail("123456"))
    println("관심사 단계 스킵 가능: ${manager5.canSkip()}")
    manager5.send(OnboardingEvent.Skip)
    println("알림 단계 스킵 가능: ${manager5.canSkip()}")
    manager5.send(OnboardingEvent.Skip)
    println("완료 상태: ${manager5.currentState()}")
    println()

    // === DSL 기반 미디어 플레이어 ===
    println("=== 7. DSL 기반 미디어 플레이어 FSM ===")
    val player = MediaPlayerExample.createPlayerFSM()

    player.addListener { from, event, to ->
        println("  ♪ $from → $to")
    }

    player.send(MediaPlayerExample.PlayerEvent.Play("song.mp3"))
    player.send(MediaPlayerExample.PlayerEvent.BufferComplete)
    player.send(MediaPlayerExample.PlayerEvent.Pause)
    player.send(MediaPlayerExample.PlayerEvent.Resume)
    player.send(MediaPlayerExample.PlayerEvent.Stop)
    println()

    println("╔══════════════════════════════════════════════════════════════╗")
    println("║                       FSM Pattern 장점                       ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 명시적 상태: 모든 가능한 상태가 sealed class로 정의      ║")
    println("║ 2. 안전한 전이: 유효하지 않은 전이를 타입 시스템이 방지      ║")
    println("║ 3. Guard 조건: 전이 전 유효성 검증을 명시적으로 정의        ║")
    println("║ 4. 전이 이력: 상태 변경 추적과 디버깅 용이                  ║")
    println("║ 5. 확장 용이: 새 상태/이벤트 추가 시 컴파일 타임 체크       ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
