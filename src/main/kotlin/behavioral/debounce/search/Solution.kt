package behavioral.debounce.search

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * 디바운스 패턴을 사용하여 연속적인 사용자 입력을 효율적으로 처리합니다.
 * 사용자가 타이핑을 멈춘 후 일정 시간이 지나야 API를 호출하도록 합니다.
 */
class Solution {
    // 디바운서 클래스: 연속적인 이벤트를 처리하는 핵심 컴포넌트
    class Debouncer(
        private val delayMillis: Long,
        private val coroutineContext: CoroutineContext = Dispatchers.Default
    ) {
        private var debounceJob: Job? = null

        // 디바운스 처리 함수
        fun debounce(action: suspend () -> Unit) {
            debounceJob?.cancel() // 이전 작업 취소
            debounceJob = CoroutineScope(coroutineContext).launch {
                delay(delayMillis)  // 지정된 시간만큼 대기
                action() // 지정된 시간 동안 새 이벤트가 없으면 액션 실행
            }
        }

        // 남은 작업을 취소하는 함수
        fun cancel() {
            debounceJob?.cancel()
        }
    }

    // 향상된 검색 컴포넌트
    class DebouncedSearchComponent(
        private val api: SearchApi,
        private val debounceTimeMillis: Long = 300
    ) {
        private val debouncer = Debouncer(debounceTimeMillis)
        private var latestQuery = ""

        // 디바운스 패턴을 적용한 사용자 입력 처리 함수
        suspend fun handleUserInput(input: String) {
            println("사용자 입력: '$input'")
            latestQuery = input

            // 디바운스 적용: 사용자가 타이핑을 멈추면 API 호출
            debouncer.debounce {
                if (input == latestQuery) { // 마지막 입력된 쿼리만 처리
                    val results = api.search(input)
                    displayResults(results)
                }
            }
        }

        private fun displayResults(results: List<String>) {
            println("화면에 결과 표시: ${results.joinToString(", ")}")
            println("-------------------------------------")
        }

        // 리소스 정리
        fun cleanup() {
            debouncer.cancel()
        }
    }
}

// 디바운스 패턴을 적용한 메인 함수
fun main() = runBlocking {
    val searchApi = SearchApi()
    val searchComponent = Solution.DebouncedSearchComponent(searchApi)

    // 사용자가 "Kotlin"을 한 글자씩 빠르게 입력하는 상황 시뮬레이션
    searchComponent.handleUserInput("K")
    searchComponent.handleUserInput("Ko")
    searchComponent.handleUserInput("Kot")
    searchComponent.handleUserInput("Kotl")
    searchComponent.handleUserInput("Kotli")
    searchComponent.handleUserInput("Kotlin")

    delay(1500) // 디바운스 시간 + API 호출 시간을 고려하여 대기
    searchComponent.cleanup()

    println("\n개선점: 디바운스 패턴을 적용하여 사용자가 타이핑을 멈춘 후에만 API 요청이 발생합니다.")
    println("이를 통해 불필요한 네트워크 요청을 줄이고, 서버 부하를 감소시키며, 사용자 경험을 개선합니다.")
}