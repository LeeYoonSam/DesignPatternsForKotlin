package structural.module.configuration

/**
 * 문제점
 * - 전역 상태 관리의 어려움
 * - 캡슐화 부재
 * - 의존성 관리 복잡
 */
class Problem {
    // 전역 변수와 함수들을 사용한 설정 관리
    object GlobalConfig {
        var apiKey: String = ""
        var apiUrl: String = ""
        var timeout: Int = 30

        fun setApiConfig(key: String, url: String) {
            apiKey = key
            apiUrl = url
        }

        fun setTimeOut(seconds: Int) {
            timeout = seconds
        }

        fun getFullApiUrl(): String {
            return "$apiUrl?key=$apiKey"
        }
    }

    class ApiClient {
        fun makeRequest() {
            // 전역 설정에 직접 접근
            val url = GlobalConfig.getFullApiUrl()
            println("Making API request to: $url")
        }
    }
}

fun main() {
    // 성공 예제
    try {
        Problem.GlobalConfig.setApiConfig("test-key", "https://api.example.com")
        Problem.GlobalConfig.setTimeOut(60)

        val client = Problem.ApiClient()
        client.makeRequest()
    } catch (e: Exception) {
        println("Error in success case: ${e.message}")
    }

    // 문제 발생 예제
    try {
        // 전역 상태가 직접 수정 가능
        Problem.GlobalConfig.apiKey = ""  // 잘못된 설정
        Problem.GlobalConfig.timeout = -1 // 잘못된 값

        val client = Problem.ApiClient()
        client.makeRequest()  // 잘못된 설정으로 인한 오류 발생 가능
    } catch (e: Exception) {
        println("Error in failure case: Configuration invalid")
    }
}