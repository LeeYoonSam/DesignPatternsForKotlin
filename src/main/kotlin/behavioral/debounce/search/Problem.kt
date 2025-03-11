package behavioral.debounce.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class Problem {
    // 검색 UI 를 시뮬레이션하는 클래스
    class SearchComponent(private val api: SearchApi) {
        // 사용자 입력 처리 함수
        suspend fun handleUserInput(input: String) {
            println("사용자 입력: '$input'")
            val results = api.search(input)
            displayResults(results)
        }

        private fun displayResults(results: List<String>) {
            println("화면에 결과 표시: ${results.joinToString(", ")}")
            println("-------------------------------------")
        }
    }
}

// 문제 상황을 시뮬레이션하는 메인 함수
fun main() = runBlocking {
    val searchApi = SearchApi()
    val searchComponent = Problem.SearchComponent(searchApi)

    // 사용자가 "Kotlin"을 한 글자씩 빠르게 입력하는 상황 시뮬레이션
    searchComponent.handleUserInput("K")
    searchComponent.handleUserInput("Ko")
    searchComponent.handleUserInput("Kot")
    searchComponent.handleUserInput("Kotl")
    searchComponent.handleUserInput("Kotli")
    searchComponent.handleUserInput("Kotlin")

    delay(1000) // 모든 API 호출이 완료될 때까지 기다림

    println("\n문제점: 사용자가 'Kotlin'을 입력하는 동안 총 6번의 API 요청이 발생했습니다.")
    println("이는 불필요한 네트워크 트래픽과 서버 부하를 증가시키고, 때로는 결과가 뒤섞일 수 있습니다.")
}