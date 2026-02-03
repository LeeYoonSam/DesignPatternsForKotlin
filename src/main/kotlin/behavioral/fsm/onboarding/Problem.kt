package behavioral.fsm.onboarding

/**
 * Finite State Machine (FSM) Pattern - Problem
 *
 * 이 파일은 FSM을 적용하지 않았을 때 복잡한 다단계 플로우에서
 * 발생하는 상태 관리 문제를 보여줍니다.
 *
 * 예시: 앱 온보딩 플로우 (약관 동의 → 프로필 설정 → 관심사 선택 → 완료)
 *
 * 문제점:
 * 1. 상태 전이 규칙이 코드 전체에 분산되어 흐름 파악 어려움
 * 2. 유효하지 않은 상태 전이를 컴파일 타임에 방지 불가
 * 3. 조건부 분기가 if/else 체인으로 복잡해짐
 * 4. 새로운 단계 추가 시 기존 코드 전체 수정 필요
 * 5. 상태 전이 로그/감사가 어려움
 */

// ========================================
// 문제가 있는 온보딩 구현
// ========================================

/**
 * 문제 1: Boolean/String 플래그로 상태 관리
 *
 * 현재 어떤 단계인지 문자열로 관리하여 오타/불일치 위험
 */
class ProblematicOnboarding {
    // 상태를 문자열/Boolean 플래그로 관리
    var currentStep: String = "terms"
    var termsAccepted: Boolean = false
    var profileCompleted: Boolean = false
    var interestsSelected: Boolean = false
    var notificationPermissionAsked: Boolean = false
    var isCompleted: Boolean = false

    // 에러 발생 여부
    var hasError: Boolean = false
    var errorMessage: String? = null

    fun acceptTerms() {
        if (currentStep != "terms") {
            println("⚠️ 잘못된 상태: $currentStep 에서 약관 동의 불가")
            return
        }
        termsAccepted = true
        currentStep = "profile"
        println("약관 동의 완료 → 프로필 설정")
    }

    fun completeProfile(name: String, age: Int) {
        // 문자열 비교 → 오타 위험 ("profile" vs "profle")
        if (currentStep != "profile") {
            println("⚠️ 잘못된 상태: $currentStep 에서 프로필 설정 불가")
            return
        }
        if (!termsAccepted) {
            println("⚠️ 약관 미동의 상태에서 프로필 설정 시도!")
            return
        }
        profileCompleted = true
        currentStep = "interests"
        println("프로필 설정 완료 → 관심사 선택")
    }

    fun selectInterests(interests: List<String>) {
        if (currentStep != "interests") {
            println("⚠️ 잘못된 상태에서 관심사 선택 시도")
            return
        }
        if (interests.isEmpty()) {
            hasError = true
            errorMessage = "최소 1개 이상 선택해주세요"
            return
        }
        interestsSelected = true
        currentStep = "notification"
        println("관심사 선택 완료 → 알림 설정")
    }

    fun handleNotificationPermission(granted: Boolean) {
        if (currentStep != "notification") return
        notificationPermissionAsked = true
        currentStep = "complete"
        isCompleted = true
        println("알림 설정: $granted → 완료")
    }

    fun goBack() {
        // 뒤로 가기 로직이 if/else 체인으로 복잡
        when (currentStep) {
            "profile" -> {
                currentStep = "terms"
                termsAccepted = false  // 이전 상태 롤백 잊으면?
            }
            "interests" -> {
                currentStep = "profile"
                // profileCompleted는 롤백 안해도 되나?
            }
            "notification" -> {
                currentStep = "interests"
            }
            "terms" -> {
                println("첫 번째 단계 - 뒤로 갈 수 없음")
            }
        }
    }

    fun skip() {
        // 어떤 단계를 스킵할 수 있는지 규칙이 불명확
        when (currentStep) {
            "interests" -> {
                currentStep = "notification"
                // 스킵이 허용되는 단계인지 어떻게 확인?
            }
            "notification" -> {
                currentStep = "complete"
                isCompleted = true
            }
            else -> println("이 단계는 스킵할 수 없습니다")
        }
    }
}

/**
 * 문제 2: 유효하지 않은 상태 전이 가능
 *
 * 런타임에서야 잘못된 전이를 발견 가능
 */
class InvalidTransitionProblem {
    fun demonstrate() {
        val onboarding = ProblematicOnboarding()

        println("=== 유효하지 않은 상태 전이 시도 ===")

        // 약관 동의 없이 프로필 설정 시도
        onboarding.completeProfile("홍길동", 25)

        // 프로필 설정 없이 관심사 선택 시도
        onboarding.selectInterests(listOf("기술", "음악"))

        // 완료 상태에서 다시 약관 동의 시도
        onboarding.currentStep = "complete"  // 외부에서 직접 상태 변경 가능!
        onboarding.acceptTerms()

        println()
        println("문제: 잘못된 전이가 런타임에서만 감지됨")
        println("문제: 외부에서 상태를 직접 변경 가능")
    }
}

/**
 * 문제 3: 조건부 전이가 복잡한 if/else 체인
 */
class ConditionalFlowProblem {
    fun determineNextStep(
        currentStep: String,
        isUnder14: Boolean,
        hasExistingAccount: Boolean,
        isProUser: Boolean
    ): String {
        // 조건이 추가될수록 복잡해지는 분기
        return when (currentStep) {
            "terms" -> {
                if (isUnder14) "parental_consent"  // 미성년자: 보호자 동의
                else "profile"
            }
            "profile" -> {
                if (hasExistingAccount) "data_migration"  // 기존 계정: 데이터 이전
                else "interests"
            }
            "interests" -> {
                if (isProUser) "pro_setup"  // Pro 사용자: 추가 설정
                else "notification"
            }
            else -> "complete"
        }
        // 새로운 조건이 추가될 때마다 모든 분기를 수정해야 함
    }
}

/**
 * 문제 4: 새로운 단계 추가가 어려움
 */
class NewStepProblem {
    fun demonstrate() {
        println("=== 새 단계 추가 시 수정이 필요한 곳 ===")
        println()
        println("'이메일 인증' 단계를 프로필 다음에 추가하려면:")
        println("  1. currentStep 문자열 상수 추가")
        println("  2. completeProfile() 에서 다음 단계 변경")
        println("  3. goBack() 의 when 분기 수정")
        println("  4. skip() 의 when 분기 수정")
        println("  5. 진행률 계산 로직 수정")
        println("  6. 모든 조건부 분기 재확인")
        println()
        println("→ 변경이 코드 전체에 분산되어 누락 위험 높음")
    }
}

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║             FSM Pattern 적용 전 문제점 데모                    ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    println("--- 1. 플래그 기반 상태 관리 ---")
    val onboarding = ProblematicOnboarding()
    onboarding.acceptTerms()
    onboarding.completeProfile("홍길동", 25)
    onboarding.selectInterests(listOf("기술"))
    onboarding.handleNotificationPermission(true)
    println("완료: ${onboarding.isCompleted}")
    println()

    println("--- 2. 유효하지 않은 전이 ---")
    InvalidTransitionProblem().demonstrate()
    println()

    println("--- 3. 새 단계 추가 어려움 ---")
    NewStepProblem().demonstrate()
    println()

    println("Solution.kt에서 FSM Pattern을 적용한 해결책을 확인하세요.")
}
