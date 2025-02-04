package structural.module.configuration

/**
 * 해결책: Module 패턴을 사용한 설정 관리
 */
class Solution {
    class ConfigurationModule private constructor() {
        companion object {
            fun create(initialConfig: Configuration): ConfigurationModule {
                return ConfigurationModule().apply {
                    this.config = initialConfig
                }
            }
        }

        data class Configuration(
            val apiKey: String,
            val apiUrl: String,
            val timeout: Int
        ) {
            init {
                require(apiKey.isNotBlank()) { "API key cannot be blank" }
                require(apiUrl.isNotBlank()) { "API URL cannot be blank" }
                require(timeout > 0) { "Timeout must be positive" }
            }
        }

        private lateinit var config: Configuration

        // Public Interface
        fun updateConfiguration(
            apiKey: String? = null,
            apiUrl: String? = null,
            timeout: Int? = null
        ) {
            config = Configuration(
                apiKey =  apiKey ?: config.apiKey,
                apiUrl =  apiKey ?: config.apiUrl,
                timeout =  timeout ?: config.timeout,
            )
        }

        fun getApiUrl(): String = config.apiUrl
        fun getTimeout(): Int = config.timeout

        // Private Implementation
        private fun getFullApiUrl(): String {
            return "${config.apiUrl}?key=${config.apiKey}"
        }

        // Client Interface
        inner class ApiClient {
            fun makeRequest() {
                val url = getFullApiUrl()
                println("Making API request to: $url with timeout: ${config.timeout}s")
            }
        }
    }
}

fun main() {
    // 성공 예제
    try {
        val configModule = Solution.ConfigurationModule.create(
            Solution.ConfigurationModule.Configuration(
                apiKey = "test-key",
                apiUrl = "https://api.example.com",
                timeout = 60
            )
        )

        // 설정 업데이트
        configModule.updateConfiguration(timeout = 30)

        // API 클라이언트 사용
        val client = configModule.ApiClient()
        client.makeRequest()
    } catch (e: Exception) {
        println("Error in success case: ${e.message}")
    }

    // 검증 예제
    try {
        // 잘못된 설정으로 모듈 생성 시도
        Solution.ConfigurationModule.create(
            Solution.ConfigurationModule.Configuration(
                apiKey = "",  // 잘못된 설정
                apiUrl = "",  // 잘못된 설정
                timeout = -1  // 잘못된 값
            )
        )
    } catch (e: IllegalArgumentException) {
        println("Validation caught invalid configuration: ${e.message}")
    }
}