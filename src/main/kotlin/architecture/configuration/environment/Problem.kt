package architecture.configuration.environment

/**
 * Configuration Pattern - 문제점
 *
 * 설정 관리가 체계적이지 않을 때 발생하는 문제들:
 * 1. 하드코딩된 설정: 코드에 직접 값 작성
 * 2. 환경 구분 없음: 개발/스테이징/프로덕션 동일 설정
 * 3. 민감 정보 노출: API 키, 비밀번호가 코드에 포함
 * 4. 설정 분산: 여러 곳에 설정이 흩어져 있음
 * 5. 타입 안전성 없음: 문자열로만 설정 관리
 * 6. 기본값 없음: 설정 누락 시 오류
 * 7. 검증 없음: 잘못된 설정 값 감지 불가
 * 8. 재시작 필요: 설정 변경 시 앱 재시작 필요
 */

// ============================================================
// 문제 1: 하드코딩된 설정
// ============================================================

class HardcodedConfigProblem {
    // 코드에 직접 값 작성 - 변경 시 재컴파일 필요
    class DatabaseService {
        private val host = "localhost"           // 하드코딩!
        private val port = 5432                  // 하드코딩!
        private val database = "myapp"           // 하드코딩!
        private val username = "admin"           // 하드코딩!
        private val password = "secret123"       // 하드코딩! 보안 위험!

        fun connect(): String {
            return "jdbc:postgresql://$host:$port/$database?user=$username&password=$password"
        }
    }

    // API 키도 하드코딩
    class PaymentService {
        private val stripeApiKey = "sk_live_abc123xyz"  // 실제 키! Git에 올라감!
        private val webhookSecret = "whsec_..."         // 하드코딩!

        fun charge(amount: Double): Boolean {
            println("Using key: $stripeApiKey")  // 로그에 키 노출!
            return true
        }
    }
}

// ============================================================
// 문제 2: 환경 구분 없음
// ============================================================

class NoEnvironmentSeparationProblem {
    // 개발/스테이징/프로덕션 구분 없이 동일 설정
    class ApiClient {
        // 프로덕션 URL이 개발 중에도 사용됨!
        private val baseUrl = "https://api.production.com"  // 위험!
        private val timeout = 30000  // 개발에서는 짧게 해도 되는데...

        fun fetch(endpoint: String): String {
            println("Calling: $baseUrl$endpoint")
            return "response"
        }
    }

    // if-else로 환경 구분 시도 - 복잡하고 실수하기 쉬움
    class ConfigWithIfElse {
        fun getBaseUrl(): String {
            val env = System.getenv("ENV")  // null일 수 있음
            return if (env == "prod") {
                "https://api.production.com"
            } else if (env == "staging") {
                "https://api.staging.com"
            } else if (env == "dev") {
                "http://localhost:8080"
            } else {
                // 기본값이 프로덕션? 위험!
                "https://api.production.com"
            }
        }

        // 모든 설정마다 이런 분기 반복...
        fun getDbHost(): String {
            val env = System.getenv("ENV")
            return if (env == "prod") {
                "db.production.internal"
            } else if (env == "staging") {
                "db.staging.internal"
            } else {
                "localhost"
            }
        }
        // ... 설정이 100개면 if-else 100개?
    }
}

// ============================================================
// 문제 3: 민감 정보 노출
// ============================================================

class SensitiveInfoExposureProblem {
    // 소스 코드에 민감 정보 직접 포함
    object Secrets {
        const val DB_PASSWORD = "super_secret_password"    // Git 히스토리에 영구 저장!
        const val JWT_SECRET = "my-jwt-secret-key-12345"   // 노출!
        const val AWS_ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE"  // AWS 키 노출!
        const val AWS_SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    }

    // 로그에 민감 정보 출력
    class InsecureLogger {
        fun logConfig() {
            println("DB Password: ${Secrets.DB_PASSWORD}")  // 로그에 비밀번호!
            println("AWS Key: ${Secrets.AWS_ACCESS_KEY}")    // 로그에 키!
        }
    }

    // 에러 메시지에 민감 정보 포함
    class InsecureErrorHandler {
        fun handleDbError(e: Exception) {
            // 스택트레이스에 연결 문자열(비밀번호 포함) 노출
            throw RuntimeException(
                "DB connection failed: jdbc:mysql://user:${Secrets.DB_PASSWORD}@localhost/db",
                e
            )
        }
    }
}

// ============================================================
// 문제 4: 설정 분산
// ============================================================

class ScatteredConfigProblem {
    // 설정이 여러 파일/클래스에 흩어져 있음
    class DatabaseConfig {
        val host = "localhost"
        val port = 5432
    }

    class CacheConfig {
        val redisHost = "localhost"  // DatabaseConfig와 중복?
        val redisPort = 6379
    }

    class ApiConfig {
        val timeout = 30000
        val retryCount = 3
    }

    class EmailConfig {
        val smtpHost = "smtp.gmail.com"
        val smtpPort = 587
    }

    // 어디서 뭘 설정하는지 찾기 어려움
    // 같은 값(localhost)이 여러 곳에...
    // 전체 설정 현황 파악 불가
}

// ============================================================
// 문제 5: 타입 안전성 없음
// ============================================================

class NoTypeSafetyProblem {
    // 모든 설정을 문자열로 관리
    class StringBasedConfig {
        private val props = mapOf(
            "db.port" to "5432",
            "cache.ttl" to "3600",
            "feature.enabled" to "true",
            "api.timeout" to "30000"
        )

        fun get(key: String): String? = props[key]

        // 사용 시 매번 변환 필요
        fun getDbPort(): Int {
            val value = get("db.port")
            return value?.toIntOrNull() ?: throw RuntimeException("Invalid db.port")
        }

        fun getCacheTtl(): Long {
            val value = get("cache.ttl")
            return value?.toLongOrNull() ?: 0L  // 실패 시 0? 의도한 건가?
        }

        fun isFeatureEnabled(): Boolean {
            val value = get("feature.enabled")
            return value == "true"  // "TRUE", "1", "yes"는?
        }
    }

    // 오타로 인한 버그
    class TypoProblem {
        private val config = mapOf("timeout" to "30000")

        fun getTimeout(): Int {
            // 오타! "timout" vs "timeout"
            val value = config["timout"]  // null 반환, 컴파일 에러 없음
            return value?.toInt() ?: 5000  // 의도치 않은 기본값 사용
        }
    }
}

// ============================================================
// 문제 6: 기본값 및 필수값 관리 없음
// ============================================================

class NoDefaultValueProblem {
    // 환경 변수 없으면 NPE 또는 의도치 않은 동작
    class UnsafeConfig {
        // null일 수 있음 - NPE 위험
        val dbHost: String = System.getenv("DB_HOST")
            ?: throw RuntimeException("DB_HOST not set")  // 앱 시작 불가

        // 기본값이 적절한가?
        val dbPort: Int = System.getenv("DB_PORT")?.toIntOrNull()
            ?: 5432  // 기본값 하드코딩

        // 선택적 설정 vs 필수 설정 구분 없음
        val apiKey: String? = System.getenv("API_KEY")
        // API_KEY가 필수인지 선택인지 불명확
    }

    // 설정 누락 시 런타임에 발견
    class LateErrorDiscovery {
        val config = mapOf<String, String>()  // 빈 설정

        fun processPayment() {
            // 결제 시점에야 설정 누락 발견!
            val apiKey = config["stripe.api.key"]
                ?: throw RuntimeException("Stripe API key not configured")
            // 이미 사용자가 결제 버튼 눌렀는데...
        }
    }
}

// ============================================================
// 문제 7: 설정 검증 없음
// ============================================================

class NoValidationProblem {
    // 잘못된 값이 들어와도 감지 못함
    class InvalidConfig {
        val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

        fun start() {
            // port가 -1이면? 999999면?
            // 런타임에야 오류 발생
            if (port < 0 || port > 65535) {
                throw RuntimeException("Invalid port: $port")
            }
        }
    }

    // URL 형식 검증 없음
    class UnvalidatedUrl {
        val apiUrl = System.getenv("API_URL") ?: "http://localhost"

        fun fetch() {
            // apiUrl이 유효한 URL인지 검증 없음
            // "not-a-url"이 들어오면 런타임 오류
            println("Fetching from: $apiUrl")
        }
    }

    // 상호 의존적인 설정 검증 없음
    class InconsistentConfig {
        val useHttps = System.getenv("USE_HTTPS") == "true"
        val port = System.getenv("PORT")?.toIntOrNull() ?: 80

        // HTTPS인데 port가 80이면? 경고 없음
        // 의도인지 실수인지 알 수 없음
    }
}

// ============================================================
// 문제 8: 동적 설정 변경 불가
// ============================================================

class NoDynamicReloadProblem {
    // 설정 변경 시 앱 재시작 필요
    class StaticConfig {
        // 시작 시 한 번만 로드
        private val logLevel = System.getenv("LOG_LEVEL") ?: "INFO"
        private val featureFlags = loadFeatureFlags()

        private fun loadFeatureFlags(): Map<String, Boolean> {
            return mapOf(
                "new_ui" to (System.getenv("FEATURE_NEW_UI") == "true"),
                "dark_mode" to (System.getenv("FEATURE_DARK_MODE") == "true")
            )
        }

        // 로그 레벨 변경하려면? 앱 재시작!
        // Feature flag 변경하려면? 앱 재시작!
        // 프로덕션에서 재시작은 다운타임...
    }

    // 설정 변경 알림 없음
    class NoConfigChangeNotification {
        var timeout = 30000

        // timeout이 변경되어도 기존 연결은 이전 값 사용
        // 변경 사실을 알 방법이 없음
    }
}

// ============================================================
// 데모
// ============================================================

fun main() {
    println("=== Configuration 관리 문제점 ===\n")

    println("1. 하드코딩된 설정")
    println("   - 변경 시 재컴파일/재배포 필요")
    println("   - 환경별 다른 값 사용 불가")

    println("\n2. 환경 구분 없음")
    println("   - 개발 중 실수로 프로덕션 DB 접근")
    println("   - if-else 분기 지옥")

    println("\n3. 민감 정보 노출")
    println("   - Git 히스토리에 비밀번호 저장")
    println("   - 로그/에러에 키 노출")

    println("\n4. 설정 분산")
    println("   - 어디서 뭘 설정하는지 파악 어려움")
    println("   - 중복 설정, 불일치")

    println("\n5. 타입 안전성 없음")
    println("   - 문자열 → 타입 변환 실패")
    println("   - 오타로 인한 버그")

    println("\n6. 기본값/필수값 관리 없음")
    println("   - 설정 누락 시 NPE")
    println("   - 런타임에 오류 발견")

    println("\n7. 설정 검증 없음")
    println("   - 잘못된 값 감지 못함")
    println("   - 상호 의존 설정 불일치")

    println("\n8. 동적 변경 불가")
    println("   - 설정 변경 시 재시작 필요")
    println("   - 다운타임 발생")

    println("\n→ 해결책: Configuration Pattern으로 체계적 설정 관리!")
}
