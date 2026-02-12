package architecture.dicontainer.container

/**
 * Dependency Injection Container Pattern - 문제점
 *
 * DI 컨테이너 없이 수동으로 의존성을 관리할 때 발생하는 문제들:
 * 1. 수동 의존성 생성: 객체 생성 코드가 여기저기 흩어짐
 * 2. 하드코딩된 의존성: new 키워드로 직접 인스턴스 생성
 * 3. 생명주기 관리 없음: 싱글톤, 스코프 관리 수동
 * 4. 테스트 어려움: Mock 주입이 어려움
 * 5. 순환 의존성: 감지 및 해결 어려움
 * 6. 의존성 그래프 복잡: 깊은 의존성 체인 관리 어려움
 * 7. 설정 변경 어려움: 환경별 다른 구현체 사용 불편
 */

// ============================================================
// 문제 1: 수동 의존성 생성 - 보일러플레이트 폭발
// ============================================================

class ManualDependencyProblem {
    // 모든 의존성을 수동으로 생성해야 함
    fun createUserService(): Any {
        // 깊은 의존성 체인
        val config = DatabaseConfig("localhost", 5432, "mydb", "user", "pass")
        val connectionPool = ConnectionPool(config, 10)
        val database = Database(connectionPool)
        val userRepository = UserRepository(database)
        val passwordEncoder = PasswordEncoder()
        val emailValidator = EmailValidator()
        val userValidator = UserValidator(emailValidator)
        val emailService = EmailService(SmtpConfig("smtp.example.com", 587))
        val eventBus = EventBus()
        val auditLogger = AuditLogger(database)

        // 드디어 UserService 생성
        return UserService(
            userRepository,
            passwordEncoder,
            userValidator,
            emailService,
            eventBus,
            auditLogger
        )
    }

    // 다른 곳에서 또 생성하면... 중복 코드
    fun createAnotherService(): Any {
        // 같은 의존성을 또 생성...
        val config = DatabaseConfig("localhost", 5432, "mydb", "user", "pass")
        val connectionPool = ConnectionPool(config, 10)
        val database = Database(connectionPool)
        // ... 또 반복
        return Unit
    }

    // 의존성 클래스들 (시뮬레이션)
    class DatabaseConfig(val host: String, val port: Int, val db: String, val user: String, val pass: String)
    class ConnectionPool(val config: DatabaseConfig, val size: Int)
    class Database(val pool: ConnectionPool)
    class UserRepository(val db: Database)
    class PasswordEncoder
    class EmailValidator
    class UserValidator(val emailValidator: EmailValidator)
    class SmtpConfig(val host: String, val port: Int)
    class EmailService(val config: SmtpConfig)
    class EventBus
    class AuditLogger(val db: Database)
    class UserService(
        val repo: UserRepository,
        val encoder: PasswordEncoder,
        val validator: UserValidator,
        val email: EmailService,
        val events: EventBus,
        val audit: AuditLogger
    )
}

// ============================================================
// 문제 2: 하드코딩된 의존성 - 결합도 증가
// ============================================================

class HardcodedDependencyProblem {
    // 내부에서 직접 의존성 생성 - 강한 결합
    class OrderService {
        // 구체 클래스에 직접 의존
        private val paymentGateway = StripePaymentGateway()  // 변경 어려움
        private val inventory = SqlInventoryRepository()      // 테스트 어려움
        private val notifier = EmailNotificationService()     // 다른 구현 사용 불가

        fun processOrder(orderId: String): Boolean {
            // 항상 Stripe, SQL, Email 사용
            val items = inventory.getItems(orderId)
            val success = paymentGateway.charge(orderId, 100.0)
            if (success) {
                notifier.notify("Order $orderId processed")
            }
            return success
        }
    }

    // PayPal로 바꾸려면? 코드 수정 필요!
    // 테스트에서 Mock 사용하려면? 불가능!

    interface PaymentGateway { fun charge(orderId: String, amount: Double): Boolean }
    class StripePaymentGateway : PaymentGateway {
        override fun charge(orderId: String, amount: Double) = true
    }

    interface InventoryRepository { fun getItems(orderId: String): List<String> }
    class SqlInventoryRepository : InventoryRepository {
        override fun getItems(orderId: String) = listOf("item1", "item2")
    }

    interface NotificationService { fun notify(message: String) }
    class EmailNotificationService : NotificationService {
        override fun notify(message: String) { println("Email: $message") }
    }
}

// ============================================================
// 문제 3: 생명주기 관리 없음 - 리소스 낭비
// ============================================================

class NoLifecycleManagementProblem {
    // 싱글톤이어야 하는데... 매번 새로 생성
    class ServiceFactory {
        fun getDatabaseConnection(): DatabaseConnection {
            // 매번 새 연결 생성 - 리소스 낭비!
            return DatabaseConnection("jdbc:mysql://localhost/db")
        }

        fun getHttpClient(): HttpClient {
            // 매번 새 클라이언트 - 커넥션 풀 비효율!
            return HttpClient()
        }
    }

    // 수동 싱글톤 관리 시도 - 번거로움
    object ManualSingletons {
        private var _dbConnection: DatabaseConnection? = null
        private var _httpClient: HttpClient? = null

        val dbConnection: DatabaseConnection
            get() {
                if (_dbConnection == null) {
                    _dbConnection = DatabaseConnection("jdbc:mysql://localhost/db")
                }
                return _dbConnection!!
            }

        val httpClient: HttpClient
            get() {
                if (_httpClient == null) {
                    _httpClient = HttpClient()
                }
                return _httpClient!!
            }

        // 각 의존성마다 이런 코드 반복...
        // 스레드 안전성? synchronized 추가해야...
        // 테스트에서 리셋? 어려움...
    }

    class DatabaseConnection(val url: String)
    class HttpClient
}

// ============================================================
// 문제 4: 테스트 어려움 - Mock 주입 불가
// ============================================================

class TestingDifficultyProblem {
    // 의존성이 내부에 하드코딩되어 Mock 주입 불가
    class PaymentProcessor {
        private val gateway = RealPaymentGateway()  // 테스트에서도 실제 결제?
        private val logger = FileLogger()           // 테스트에서도 파일 쓰기?

        fun process(amount: Double): Boolean {
            logger.log("Processing payment: $amount")
            return gateway.charge(amount)  // 실제 API 호출!
        }
    }

    // 테스트 작성
    fun testPaymentProcessor() {
        val processor = PaymentProcessor()
        // Mock을 주입할 방법이 없음!
        // 테스트하려면 실제 결제 API 호출됨...
        // 파일에 로그가 쓰여짐...
        val result = processor.process(100.0)
        // 어떻게 검증?
    }

    class RealPaymentGateway { fun charge(amount: Double) = true }
    class FileLogger { fun log(message: String) { println("FILE: $message") } }
}

// ============================================================
// 문제 5: 순환 의존성 감지 불가
// ============================================================

class CircularDependencyProblem {
    // A → B → C → A 순환 의존성
    // 컴파일은 되지만 런타임에 문제 발생

    class ServiceA(val b: ServiceB) {
        fun doSomething() = b.doSomething()
    }

    class ServiceB(val c: ServiceC) {
        fun doSomething() = c.doSomething()
    }

    class ServiceC(val a: ServiceA) {  // 순환!
        fun doSomething() = a.doSomething()  // 무한 루프 또는 StackOverflow
    }

    // 수동으로 생성하면 감지 어려움
    fun createServices(): ServiceA {
        // 이 코드는 컴파일이 안됨 (다행히)
        // 하지만 Lazy나 Provider로 우회하면 런타임에 문제 발생
        // val a = ServiceA(b)  // b가 아직 없음
        // val b = ServiceB(c)
        // val c = ServiceC(a)
        throw UnsupportedOperationException("Cannot create circular dependencies")
    }
}

// ============================================================
// 문제 6: 환경별 설정 변경 어려움
// ============================================================

class EnvironmentConfigProblem {
    // 환경에 따라 다른 구현체 사용해야 함
    class ApplicationBootstrap {
        fun createServices(): Services {
            val env = System.getenv("ENV") ?: "dev"

            // if-else 지옥
            val database = if (env == "prod") {
                ProductionDatabase("prod-host", 5432)
            } else if (env == "staging") {
                ProductionDatabase("staging-host", 5432)
            } else {
                InMemoryDatabase()
            }

            val cache = if (env == "prod") {
                RedisCache("redis-cluster.prod")
            } else {
                InMemoryCache()
            }

            val emailService = if (env == "prod") {
                SmtpEmailService("smtp.prod.com")
            } else {
                ConsoleEmailService()  // 개발 환경에서는 콘솔 출력
            }

            // 모든 의존성에 대해 이런 분기...
            // 새 환경 추가하면? 모든 분기에 else if 추가...

            return Services(database, cache, emailService)
        }
    }

    interface Database
    class ProductionDatabase(val host: String, val port: Int) : Database
    class InMemoryDatabase : Database

    interface Cache
    class RedisCache(val host: String) : Cache
    class InMemoryCache : Cache

    interface EmailService
    class SmtpEmailService(val host: String) : EmailService
    class ConsoleEmailService : EmailService

    data class Services(val db: Database, val cache: Cache, val email: EmailService)
}

// ============================================================
// 문제 7: 의존성 변경 시 수정 범위 넓음
// ============================================================

class DependencyChangePropagationProblem {
    // UserRepository에 새 의존성 추가되면?
    // UserRepository를 사용하는 모든 곳 수정 필요!

    // 기존 코드
    class UserRepositoryV1(val db: Database)

    // 새 의존성 추가 (Cache)
    class UserRepositoryV2(val db: Database, val cache: Cache)  // 시그니처 변경!

    // 영향받는 모든 곳 수정 필요...
    class UserServiceOld(val repo: UserRepositoryV1)  // 여기도
    class AdminServiceOld(val repo: UserRepositoryV1)  // 여기도
    class ReportServiceOld(val repo: UserRepositoryV1) // 여기도

    // 각각 생성하는 Factory도 전부 수정...

    interface Database
    interface Cache
}

// ============================================================
// 데모
// ============================================================

fun main() {
    println("=== DI Container 없이 의존성 관리 시 문제점 ===\n")

    println("1. 수동 의존성 생성: 보일러플레이트 코드 폭발")
    println("   - 의존성 체인이 깊을수록 생성 코드 증가")
    println("   - 여러 곳에서 같은 의존성 생성 시 중복 코드")

    println("\n2. 하드코딩된 의존성: 결합도 증가")
    println("   - 구체 클래스에 직접 의존")
    println("   - 구현체 변경 시 코드 수정 필요")

    println("\n3. 생명주기 관리 없음: 리소스 낭비")
    println("   - 싱글톤이어야 할 객체 매번 생성")
    println("   - 수동 싱글톤 관리는 번거롭고 오류 발생 쉬움")

    println("\n4. 테스트 어려움: Mock 주입 불가")
    println("   - 내부 하드코딩된 의존성은 교체 불가")
    println("   - 단위 테스트에서 실제 외부 서비스 호출")

    println("\n5. 순환 의존성 감지 불가")
    println("   - 런타임에 StackOverflow 발생")
    println("   - 코드 리뷰로만 발견 가능")

    println("\n6. 환경별 설정 변경 어려움")
    println("   - if-else 분기 지옥")
    println("   - 새 환경 추가 시 모든 분기 수정")

    println("\n7. 의존성 변경 시 전파")
    println("   - 의존성 시그니처 변경 시 모든 사용처 수정")
    println("   - 리팩토링 범위 예측 어려움")

    println("\n→ 해결책: DI 컨테이너로 의존성 자동 관리!")
}
