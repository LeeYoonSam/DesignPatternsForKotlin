# Finite State Machine (FSM) Pattern

## 개요

Finite State Machine(유한 상태 기계)은 시스템이 가질 수 있는 **유한한 상태 집합**과 **상태 간 전이 규칙**을 명시적으로 정의하는 패턴입니다. 현재 상태와 입력(이벤트)에 따라 다음 상태가 결정되며, 유효하지 않은 상태 전이를 컴파일 타임에 방지할 수 있습니다.

## 핵심 개념

### 구성 요소

| 구성 요소 | 설명 | 예시 |
|-----------|------|------|
| **State (상태)** | 시스템이 있을 수 있는 유한한 상태 | 약관동의, 프로필설정, 완료 |
| **Event (이벤트)** | 상태 전이를 유발하는 입력 | 약관수락, 프로필완료, 스킵 |
| **Transition (전이)** | 현재 상태 + 이벤트 → 다음 상태 규칙 | 약관동의 + 수락 → 프로필설정 |
| **Guard (가드)** | 전이가 허용되는 조건 | 이름이 비어있지 않은 경우만 |
| **Action (액션)** | 전이 시 실행되는 부수효과 | 이메일 발송, 프로필 저장 |

### 상태 다이어그램

```
                     AcceptTerms(under14)
                    ┌──────────────────────► ParentalConsent
                    │                              │
                    │                    ParentalConsentGranted
  ┌──────────────┐  │  AcceptTerms      ┌──────────┘
  │   Terms      │──┴──────────────────►│ Profile  │
  │  Agreement   │                      │  Setup   │
  └──────────────┘                      └────┬─────┘
                                             │ CompleteProfile
                                             ▼
                                      ┌──────────────┐
                                      │    Email     │
                                      │ Verification │
                                      └──────┬───────┘
                                             │ VerifyEmail
                                             ▼
  ┌──────────────┐  Skip/Select     ┌──────────────┐
  │ Notification │◄─────────────────│  Interest    │
  │  Permission  │                  │  Selection   │
  └──────┬───────┘                  └──────────────┘
         │ HandleNotification/Skip
         ▼
  ┌──────────────┐
  │  Completed   │
  └──────────────┘

  * 모든 단계에서 GoBack으로 이전 단계 복귀 가능
  * Error 상태에서 RetryFromError로 이전 상태 복구 가능
```

## State Pattern vs FSM Pattern

| 측면 | State Pattern | FSM Pattern |
|------|---------------|-------------|
| 초점 | 상태별 행동 캡슐화 | 상태 전이 규칙 명시 |
| 전이 정의 | 각 State 클래스 내부 | 한 곳에 중앙화 (Transition Table) |
| Guard 조건 | 별도 구현 필요 | 패턴에 내장 |
| 전이 로그 | 별도 구현 필요 | 엔진에서 자동 기록 |
| 적합한 경우 | 상태별 행동이 복잡할 때 | 전이 규칙이 복잡할 때 |
| 사용 예 | 주문 상태 처리 | 온보딩 플로우, 게임 AI |

## 구현 상세

### 1. 타입 안전한 상태/이벤트 (sealed class)

```kotlin
sealed class OnboardingState {
    object TermsAgreement : OnboardingState()
    data class ParentalConsent(val isUnder14: Boolean) : OnboardingState()
    object ProfileSetup : OnboardingState()
    data class EmailVerification(val email: String) : OnboardingState()
    object InterestSelection : OnboardingState()
    object NotificationPermission : OnboardingState()
    data class Completed(val userId: String) : OnboardingState()
    data class Error(val previousState: OnboardingState, val message: String) : OnboardingState()
}

sealed class OnboardingEvent {
    data class AcceptTerms(val isUnder14: Boolean = false) : OnboardingEvent()
    data class CompleteProfile(val name: String, val email: String, val age: Int) : OnboardingEvent()
    data class VerifyEmail(val code: String) : OnboardingEvent()
    object Skip : OnboardingEvent()
    object GoBack : OnboardingEvent()
    object RetryFromError : OnboardingEvent()
    // ...
}
```

### 2. 범용 FSM 엔진

```kotlin
class StateMachine<S, E>(
    initialState: S,
    private val transitionHandler: (currentState: S, event: E) -> TransitionResult
) {
    var currentState: S = initialState
        private set

    fun send(event: E): TransitionResult {
        val result = transitionHandler(currentState, event)
        when (result) {
            is TransitionResult.Success -> {
                val previous = currentState
                currentState = result.newState as S
                // 로그 기록, 리스너 알림, 액션 실행
            }
            is TransitionResult.Denied -> {
                // 거부된 전이 처리
            }
        }
        return result
    }
}
```

### 3. 전이 규칙 정의

```kotlin
object OnboardingTransitions {
    fun handle(currentState: OnboardingState, event: OnboardingEvent): TransitionResult =
        when (currentState) {
            is OnboardingState.TermsAgreement -> when (event) {
                is OnboardingEvent.AcceptTerms -> {
                    if (event.isUnder14) {
                        TransitionResult.Success(OnboardingState.ParentalConsent(true))
                    } else {
                        TransitionResult.Success(OnboardingState.ProfileSetup)
                    }
                }
                is OnboardingEvent.DeclineTerms ->
                    TransitionResult.Denied("약관에 동의해야 진행할 수 있습니다")
                else -> TransitionResult.Denied("허용되지 않는 이벤트")
            }
            // ... 모든 전이 규칙이 한 곳에 정의
        }
}
```

### 4. Guard 조건

```kotlin
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
                actions = listOf(Action.SaveProfile(event.name, event.email))
            )
        }
    }
}
```

### 5. DSL 기반 FSM 빌더

```kotlin
val playerFSM = stateMachine<PlayerState, PlayerEvent>(PlayerState.Idle) {
    state<PlayerState.Idle> {
        on<PlayerEvent.Play> { _, _ ->
            TransitionResult.Success(PlayerState.Buffering)
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
    // ...
}
```

## 에러 복구 패턴

```kotlin
// Error 상태가 이전 상태를 기억
data class Error(val previousState: OnboardingState, val message: String) : OnboardingState()

// RetryFromError로 이전 상태로 복구
is OnboardingState.Error -> when (event) {
    is OnboardingEvent.RetryFromError ->
        TransitionResult.Success(newState = currentState.previousState)
    is OnboardingEvent.GoBack ->
        TransitionResult.Success(newState = currentState.previousState)
    else -> TransitionResult.Denied("에러 상태에서는 재시도 또는 뒤로가기만 가능")
}
```

## 전이 이력 (Audit Trail)

```kotlin
data class TransitionRecord<S, E>(
    val fromState: S,
    val event: E,
    val toState: S,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// 전이마다 자동 기록
fun getTransitionHistory(): List<TransitionRecord<S, E>> = transitionLog.toList()

// 출력 예:
// 1. TermsAgreement → ProfileSetup (AcceptTerms)
// 2. ProfileSetup → EmailVerification (CompleteProfile)
// 3. EmailVerification → InterestSelection (VerifyEmail)
```

## 장점

1. **명시적 상태**: 모든 가능한 상태가 sealed class로 정의되어 누락 방지
2. **안전한 전이**: 유효하지 않은 전이를 타입 시스템이 방지 (when exhaustive check)
3. **Guard 조건**: 전이 전 유효성 검증을 명시적으로 정의
4. **중앙화된 규칙**: 모든 전이 규칙이 한 곳에 정의되어 플로우 파악 용이
5. **전이 이력**: 상태 변경 추적과 디버깅 용이 (Time Travel Debugging)
6. **확장 용이**: 새 상태/이벤트 추가 시 컴파일 타임 체크로 누락 방지
7. **에러 복구**: Error 상태에서 이전 상태로 복구하는 패턴 내장

## 단점

1. **상태 폭발**: 상태/이벤트 조합이 많아지면 전이 테이블이 커짐
2. **복잡한 병행**: 동시에 여러 상태를 갖는 경우 처리 어려움 (Statecharts 필요)
3. **보일러플레이트**: State, Event, Action 등 많은 타입 정의 필요

## 적용 시점

- 다단계 플로우 (온보딩, 결제, 회원가입)
- 게임 AI (적 캐릭터 행동 패턴)
- 미디어 플레이어 (재생/일시정지/정지)
- 네트워크 연결 상태 관리
- UI 상태 관리 (로딩/성공/에러)
- 워크플로우 엔진 (승인 프로세스)

## 관련 패턴

- **State Pattern**: 상태별 행동 캡슐화에 초점, FSM은 전이 규칙에 초점
- **Strategy Pattern**: 상태에 따른 전략 변경과 유사
- **Command Pattern**: 이벤트(Intent)가 Command와 유사
- **Observer Pattern**: 상태 전이 리스너에 사용
- **MVI Pattern**: UI 레이어에서의 상태 관리, FSM과 결합 가능

## 참고 자료

- [Tinder - State Machine Library](https://github.com/Tinder/StateMachine)
- [Wikipedia - Finite-state machine](https://en.wikipedia.org/wiki/Finite-state_machine)
- [Statecharts - David Harel](https://www.sciencedirect.com/science/article/pii/0167642387900359)
- [XState - JavaScript FSM Library](https://xstate.js.org/)
