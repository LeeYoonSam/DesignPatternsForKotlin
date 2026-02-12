package architecture.dicontainer.container

import kotlin.reflect.KClass

/**
 * Dependency Injection Container Pattern - 해결책
 *
 * Koin/Dagger 스타일의 DI 컨테이너를 직접 구현:
 * - Module: 의존성 정의 모듈
 * - Container: 의존성 해결 및 생명주기 관리
 * - Scope: 싱글톤, 팩토리, 스코프별 인스턴스 관리
 * - Qualifier: 같은 타입의 다른 구현체 구분
 * - DSL: 선언적 의존성 정의
 *
 * 핵심 구성:
 * - Definition: 의존성 생성 방법 정의
 * - Module: 관련 의존성들의 그룹
 * - Container: 의존성 저장소 및 해결기
 * - Scope: 생명주기 관리 (Singleton, Factory, Scoped)
 */

// ============================================================
// 1. 기본 타입 정의
// ============================================================

/**
 * 의존성 생명주기
 */
enum class Lifecycle {
    SINGLETON,   // 앱 전체에서 단일 인스턴스
    FACTORY,     // 매번 새 인스턴스 생성
    SCOPED       // 특정 스코프 내에서 단일 인스턴스
}

/**
 * 의존성 정의
 */
data class Definition<T : Any>(
    val type: KClass<T>,
    val qualifier: String? = null,
    val lifecycle: Lifecycle = Lifecycle.SINGLETON,
    val scopeId: String? = null,
    val factory: DefinitionContext.() -> T
)

/**
 * 정의 컨텍스트 - 의존성 해결 시 사용
 */
class DefinitionContext(private val container: DIContainer) {
    /**
     * 다른 의존성 가져오기
     */
    inline fun <reified T : Any> get(qualifier: String? = null): T {
        return container.get(T::class, qualifier)
    }

    /**
     * 지연 의존성 (순환 의존성 해결용)
     */
    inline fun <reified T : Any> lazy(qualifier: String? = null): Lazy<T> {
        return kotlin.lazy { container.get(T::class, qualifier) }
    }

    /**
     * 파라미터 가져오기
     */
    fun <T> getParameter(key: String): T {
        @Suppress("UNCHECKED_CAST")
        return container.getParameter(key) as T
    }
}

// ============================================================
// 2. Module - 의존성 정의 그룹
// ============================================================

/**
 * 의존성 모듈
 */
class Module(val name: String = "unnamed") {
    internal val definitions = mutableListOf<Definition<*>>()

    /**
     * 싱글톤 정의
     */
    inline fun <reified T : Any> single(
        qualifier: String? = null,
        noinline factory: DefinitionContext.() -> T
    ) {
        definitions.add(
            Definition(
                type = T::class,
                qualifier = qualifier,
                lifecycle = Lifecycle.SINGLETON,
                factory = factory
            )
        )
    }

    /**
     * 팩토리 정의 (매번 새 인스턴스)
     */
    inline fun <reified T : Any> factory(
        qualifier: String? = null,
        noinline factory: DefinitionContext.() -> T
    ) {
        definitions.add(
            Definition(
                type = T::class,
                qualifier = qualifier,
                lifecycle = Lifecycle.FACTORY,
                factory = factory
            )
        )
    }

    /**
     * 스코프 정의
     */
    inline fun <reified T : Any> scoped(
        scopeId: String,
        qualifier: String? = null,
        noinline factory: DefinitionContext.() -> T
    ) {
        definitions.add(
            Definition(
                type = T::class,
                qualifier = qualifier,
                lifecycle = Lifecycle.SCOPED,
                scopeId = scopeId,
                factory = factory
            )
        )
    }
}

/**
 * 모듈 DSL
 */
fun module(name: String = "unnamed", block: Module.() -> Unit): Module {
    return Module(name).apply(block)
}

// ============================================================
// 3. DI Container - 핵심 컨테이너
// ============================================================

/**
 * 의존성 주입 컨테이너
 */
class DIContainer {
    private val definitions = mutableMapOf<DefinitionKey, Definition<*>>()
    private val singletons = mutableMapOf<DefinitionKey, Any>()
    private val scopes = mutableMapOf<String, MutableMap<DefinitionKey, Any>>()
    private val parameters = mutableMapOf<String, Any>()

    // 순환 의존성 감지용
    private val resolutionStack = ThreadLocal<MutableSet<DefinitionKey>>()

    /**
     * 모듈 로드
     */
    fun loadModules(vararg modules: Module) {
        modules.forEach { module ->
            module.definitions.forEach { definition ->
                val key = DefinitionKey(definition.type, definition.qualifier)

                // 중복 정의 경고
                if (definitions.containsKey(key)) {
                    println("[DI] Warning: Overriding definition for ${key.type.simpleName} (qualifier=${key.qualifier})")
                }

                definitions[key] = definition
            }
            println("[DI] Module '${module.name}' loaded: ${module.definitions.size} definitions")
        }
    }

    /**
     * 파라미터 설정
     */
    fun setParameter(key: String, value: Any) {
        parameters[key] = value
    }

    fun getParameter(key: String): Any {
        return parameters[key] ?: throw IllegalArgumentException("Parameter '$key' not found")
    }

    /**
     * 의존성 가져오기
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>, qualifier: String? = null): T {
        val key = DefinitionKey(type, qualifier)
        val definition = definitions[key]
            ?: throw IllegalStateException(
                "No definition found for ${type.simpleName} (qualifier=$qualifier). " +
                "Available: ${definitions.keys.map { "${it.type.simpleName}(${it.qualifier})" }}"
            )

        return resolve(key, definition as Definition<T>)
    }

    /**
     * 인라인 get
     */
    inline fun <reified T : Any> get(qualifier: String? = null): T {
        return get(T::class, qualifier)
    }

    /**
     * 의존성 해결
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolve(key: DefinitionKey, definition: Definition<T>): T {
        // 순환 의존성 감지
        val stack = resolutionStack.get() ?: mutableSetOf<DefinitionKey>().also { resolutionStack.set(it) }

        if (key in stack) {
            val chain = stack.joinToString(" → ") { it.type.simpleName ?: "?" } + " → ${key.type.simpleName}"
            throw CircularDependencyException("Circular dependency detected: $chain")
        }

        stack.add(key)

        try {
            return when (definition.lifecycle) {
                Lifecycle.SINGLETON -> {
                    singletons.getOrPut(key) {
                        createInstance(definition)
                    } as T
                }

                Lifecycle.FACTORY -> {
                    createInstance(definition)
                }

                Lifecycle.SCOPED -> {
                    val scopeId = definition.scopeId
                        ?: throw IllegalStateException("Scoped definition requires scopeId")
                    val scopeInstances = scopes.getOrPut(scopeId) { mutableMapOf() }
                    scopeInstances.getOrPut(key) {
                        createInstance(definition)
                    } as T
                }
            }
        } finally {
            stack.remove(key)
            if (stack.isEmpty()) {
                resolutionStack.remove()
            }
        }
    }

    /**
     * 인스턴스 생성
     */
    private fun <T : Any> createInstance(definition: Definition<T>): T {
        val context = DefinitionContext(this)
        return definition.factory(context)
    }

    /**
     * 스코프 생성
     */
    fun createScope(scopeId: String): Scope {
        scopes.putIfAbsent(scopeId, mutableMapOf())
        return Scope(scopeId, this)
    }

    /**
     * 스코프 종료
     */
    fun closeScope(scopeId: String) {
        val instances = scopes.remove(scopeId)
        instances?.values?.forEach { instance ->
            if (instance is AutoCloseable) {
                try {
                    instance.close()
                } catch (e: Exception) {
                    println("[DI] Error closing ${instance::class.simpleName}: ${e.message}")
                }
            }
        }
        println("[DI] Scope '$scopeId' closed")
    }

    /**
     * 의존성 그래프 출력
     */
    fun printDependencyGraph() {
        println("\n=== Dependency Graph ===")
        definitions.forEach { (key, definition) ->
            val lifecycle = definition.lifecycle.name.lowercase()
            val scope = definition.scopeId?.let { " (scope=$it)" } ?: ""
            println("${key.type.simpleName}${key.qualifier?.let { "[$it]" } ?: ""} [$lifecycle]$scope")
        }
        println("========================\n")
    }

    /**
     * 컨테이너 정리
     */
    fun close() {
        // 스코프 정리
        scopes.keys.toList().forEach { closeScope(it) }

        // 싱글톤 정리
        singletons.values.forEach { instance ->
            if (instance is AutoCloseable) {
                try {
                    instance.close()
                } catch (e: Exception) {
                    println("[DI] Error closing ${instance::class.simpleName}: ${e.message}")
                }
            }
        }
        singletons.clear()
        definitions.clear()
        println("[DI] Container closed")
    }
}

/**
 * 정의 키
 */
data class DefinitionKey(
    val type: KClass<*>,
    val qualifier: String?
)

/**
 * 순환 의존성 예외
 */
class CircularDependencyException(message: String) : RuntimeException(message)

// ============================================================
// 4. Scope - 생명주기 관리
// ============================================================

/**
 * 스코프
 */
class Scope(
    val id: String,
    private val container: DIContainer
) : AutoCloseable {
    inline fun <reified T : Any> get(qualifier: String? = null): T {
        return container.get(qualifier)
    }

    override fun close() {
        container.closeScope(id)
    }
}

// ============================================================
// 5. 전역 컨테이너 (Koin 스타일)
// ============================================================

object DI {
    private var container: DIContainer? = null

    /**
     * DI 시작
     */
    fun start(block: DIBuilder.() -> Unit) {
        val builder = DIBuilder()
        builder.block()
        container = DIContainer().apply {
            loadModules(*builder.modules.toTypedArray())
        }
        println("[DI] Started with ${builder.modules.size} modules")
    }

    /**
     * 의존성 가져오기
     */
    inline fun <reified T : Any> get(qualifier: String? = null): T {
        return container?.get(qualifier)
            ?: throw IllegalStateException("DI not started. Call DI.start { } first.")
    }

    /**
     * 스코프 생성
     */
    fun createScope(scopeId: String): Scope {
        return container?.createScope(scopeId)
            ?: throw IllegalStateException("DI not started")
    }

    /**
     * DI 종료
     */
    fun stop() {
        container?.close()
        container = null
        println("[DI] Stopped")
    }

    /**
     * 의존성 그래프 출력
     */
    fun printGraph() {
        container?.printDependencyGraph()
    }
}

class DIBuilder {
    val modules = mutableListOf<Module>()

    fun modules(vararg modules: Module) {
        this.modules.addAll(modules)
    }
}

// ============================================================
// 6. Property Delegate (by inject())
// ============================================================

/**
 * 지연 주입 델리게이트
 */
inline fun <reified T : Any> inject(qualifier: String? = null): Lazy<T> {
    return lazy { DI.get<T>(qualifier) }
}

// ============================================================
// 7. 사용 예시 - 도메인 클래스들
// ============================================================

// --- 인터페이스 ---

interface Logger {
    fun log(message: String)
}

interface Database {
    fun query(sql: String): List<Map<String, Any>>
    fun execute(sql: String): Int
}

interface UserRepository {
    fun findById(id: String): User?
    fun findAll(): List<User>
    fun save(user: User): User
}

interface EmailService {
    fun send(to: String, subject: String, body: String)
}

interface PaymentGateway {
    fun charge(amount: Double): Boolean
}

// --- 구현체 ---

class ConsoleLogger : Logger {
    override fun log(message: String) {
        println("[LOG] $message")
    }
}

class FileLogger(private val filePath: String) : Logger {
    override fun log(message: String) {
        println("[FILE:$filePath] $message")
    }
}

class InMemoryDatabase : Database, AutoCloseable {
    private val data = mutableMapOf<String, MutableList<Map<String, Any>>>()

    init {
        println("[DB] InMemoryDatabase created")
    }

    override fun query(sql: String): List<Map<String, Any>> {
        println("[DB] Query: $sql")
        return data.values.flatten()
    }

    override fun execute(sql: String): Int {
        println("[DB] Execute: $sql")
        return 1
    }

    override fun close() {
        println("[DB] InMemoryDatabase closed")
        data.clear()
    }
}

class PostgresDatabase(
    private val host: String,
    private val port: Int,
    private val dbName: String
) : Database, AutoCloseable {
    init {
        println("[DB] PostgresDatabase connected to $host:$port/$dbName")
    }

    override fun query(sql: String): List<Map<String, Any>> {
        println("[DB:$dbName] Query: $sql")
        return emptyList()
    }

    override fun execute(sql: String): Int {
        println("[DB:$dbName] Execute: $sql")
        return 1
    }

    override fun close() {
        println("[DB] PostgresDatabase connection closed")
    }
}

data class User(val id: String, val name: String, val email: String)

class UserRepositoryImpl(
    private val database: Database,
    private val logger: Logger
) : UserRepository {
    override fun findById(id: String): User? {
        logger.log("Finding user by id: $id")
        database.query("SELECT * FROM users WHERE id = '$id'")
        return User(id, "User $id", "user$id@example.com")
    }

    override fun findAll(): List<User> {
        logger.log("Finding all users")
        database.query("SELECT * FROM users")
        return listOf(
            User("1", "Alice", "alice@example.com"),
            User("2", "Bob", "bob@example.com")
        )
    }

    override fun save(user: User): User {
        logger.log("Saving user: ${user.id}")
        database.execute("INSERT INTO users VALUES (...)")
        return user
    }
}

class SmtpEmailService(
    private val host: String,
    private val port: Int,
    private val logger: Logger
) : EmailService {
    override fun send(to: String, subject: String, body: String) {
        logger.log("Sending email to $to via $host:$port")
        println("[EMAIL] To: $to, Subject: $subject")
    }
}

class MockEmailService(private val logger: Logger) : EmailService {
    val sentEmails = mutableListOf<Triple<String, String, String>>()

    override fun send(to: String, subject: String, body: String) {
        logger.log("[MOCK] Email to $to: $subject")
        sentEmails.add(Triple(to, subject, body))
    }
}

class StripeGateway(private val apiKey: String) : PaymentGateway {
    override fun charge(amount: Double): Boolean {
        println("[Stripe:$apiKey] Charging $amount")
        return true
    }
}

class MockPaymentGateway : PaymentGateway {
    var shouldSucceed = true
    var chargedAmount: Double? = null

    override fun charge(amount: Double): Boolean {
        chargedAmount = amount
        return shouldSucceed
    }
}

// --- 서비스 ---

class UserService(
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val logger: Logger
) {
    fun registerUser(name: String, email: String): User {
        logger.log("Registering user: $name")

        val user = User(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = name,
            email = email
        )

        val saved = userRepository.save(user)
        emailService.send(email, "Welcome!", "Hello $name, welcome to our service!")

        return saved
    }

    fun getUser(id: String): User? {
        return userRepository.findById(id)
    }

    fun getAllUsers(): List<User> {
        return userRepository.findAll()
    }
}

class OrderService(
    private val paymentGateway: PaymentGateway,
    private val emailService: EmailService,
    private val logger: Logger
) {
    fun processOrder(orderId: String, amount: Double, customerEmail: String): Boolean {
        logger.log("Processing order: $orderId, amount: $amount")

        val success = paymentGateway.charge(amount)

        if (success) {
            emailService.send(customerEmail, "Order Confirmed", "Order $orderId has been processed.")
            logger.log("Order $orderId completed successfully")
        } else {
            logger.log("Order $orderId failed")
        }

        return success
    }
}

// --- Session Scoped ---

data class UserSession(val userId: String, val token: String) {
    init {
        println("[Session] Created for user: $userId")
    }
}

class SessionService(
    private val session: UserSession,
    private val logger: Logger
) {
    fun getCurrentUser(): String {
        logger.log("Getting current user from session")
        return session.userId
    }
}

// ============================================================
// 8. 모듈 정의
// ============================================================

val coreModule = module("core") {
    // 로거 - 싱글톤
    single<Logger> { ConsoleLogger() }

    // 파일 로거 - qualifier로 구분
    single<Logger>(qualifier = "file") {
        FileLogger("/var/log/app.log")
    }
}

val databaseModule = module("database") {
    // 개발용 인메모리 DB
    single<Database>(qualifier = "dev") {
        InMemoryDatabase()
    }

    // 프로덕션 PostgreSQL
    single<Database>(qualifier = "prod") {
        PostgresDatabase(
            host = getParameter("db.host"),
            port = getParameter("db.port"),
            dbName = getParameter("db.name")
        )
    }
}

val repositoryModule = module("repository") {
    single<UserRepository> {
        UserRepositoryImpl(
            database = get(qualifier = "dev"),  // 개발 DB 사용
            logger = get()
        )
    }
}

val serviceModule = module("service") {
    // 이메일 서비스
    single<EmailService> {
        MockEmailService(get())  // 개발환경에서는 Mock
    }

    // 결제 게이트웨이
    single<PaymentGateway> {
        MockPaymentGateway()
    }

    // UserService
    single {
        UserService(
            userRepository = get(),
            emailService = get(),
            logger = get()
        )
    }

    // OrderService
    single {
        OrderService(
            paymentGateway = get(),
            emailService = get(),
            logger = get()
        )
    }
}

val sessionModule = module("session") {
    // Session - 스코프별 인스턴스
    scoped<UserSession>("user-session") {
        UserSession(
            userId = getParameter("userId"),
            token = getParameter("token")
        )
    }

    // SessionService - 스코프별
    scoped<SessionService>("user-session") {
        SessionService(
            session = get(),
            logger = get()
        )
    }
}

// ============================================================
// 9. 프로덕션 모듈 (환경별 설정)
// ============================================================

fun createProductionModules(): List<Module> = listOf(
    module("prod-core") {
        single<Logger>(qualifier = "file") {
            FileLogger("/var/log/app.log")
        }
        single<Logger> { get(qualifier = "file") }
    },

    module("prod-database") {
        single<Database> {
            PostgresDatabase(
                host = getParameter("db.host"),
                port = getParameter("db.port"),
                dbName = getParameter("db.name")
            )
        }
    },

    module("prod-services") {
        single<EmailService> {
            SmtpEmailService(
                host = getParameter("smtp.host"),
                port = getParameter<Int>("smtp.port"),
                logger = get()
            )
        }

        single<PaymentGateway> {
            StripeGateway(getParameter("stripe.apiKey"))
        }
    }
)

// ============================================================
// 10. 테스트용 모듈
// ============================================================

fun createTestModules(): List<Module> = listOf(
    module("test-core") {
        single<Logger> { ConsoleLogger() }
    },

    module("test-database") {
        single<Database> { InMemoryDatabase() }
    },

    module("test-services") {
        // Mock 이메일 서비스
        single<EmailService> {
            MockEmailService(get())
        }

        // Mock 결제 게이트웨이
        single<PaymentGateway> {
            MockPaymentGateway()
        }
    }
)

// ============================================================
// 11. 데모
// ============================================================

fun main() {
    println("=== Dependency Injection Container Pattern ===\n")

    // --- 1. DI 컨테이너 시작 ---
    println("--- 1. DI 컨테이너 시작 ---")
    DI.start {
        modules(
            coreModule,
            databaseModule,
            repositoryModule,
            serviceModule,
            sessionModule
        )
    }

    // 의존성 그래프 출력
    DI.printGraph()

    // --- 2. 의존성 주입 사용 ---
    println("--- 2. 의존성 주입 사용 ---")

    // 직접 가져오기
    val userService: UserService = DI.get()
    val logger: Logger = DI.get()

    logger.log("Application started")

    // 사용자 등록
    val user = userService.registerUser("Alice", "alice@example.com")
    println("Registered: $user\n")

    // 사용자 조회
    val allUsers = userService.getAllUsers()
    println("All users: $allUsers\n")

    // --- 3. Qualifier 사용 ---
    println("--- 3. Qualifier 사용 ---")
    val consoleLogger: Logger = DI.get()
    val fileLogger: Logger = DI.get(qualifier = "file")

    consoleLogger.log("Console message")
    fileLogger.log("File message")
    println()

    // --- 4. Lazy Injection ---
    println("--- 4. Lazy Injection ---")
    val lazyOrderService: OrderService by inject()

    // 아직 생성 안됨 (이미 싱글톤이므로 캐시됨)
    println("OrderService not accessed yet")

    // 접근 시 주입
    lazyOrderService.processOrder("ORD-001", 99.99, "customer@example.com")
    println()

    // --- 5. Scope 사용 ---
    println("--- 5. Scope 사용 (세션) ---")

    // 사용자 A 세션
    val container = DIContainer().apply {
        loadModules(coreModule, sessionModule)
        setParameter("userId", "user-A")
        setParameter("token", "token-A-123")
    }

    val scopeA = container.createScope("user-session")
    println("Scope A created")

    // 사용자 B 세션 (다른 스코프)
    val containerB = DIContainer().apply {
        loadModules(coreModule, sessionModule)
        setParameter("userId", "user-B")
        setParameter("token", "token-B-456")
    }

    val scopeB = containerB.createScope("user-session")
    println("Scope B created")

    // 스코프 종료
    scopeA.close()
    scopeB.close()
    container.close()
    containerB.close()
    println()

    // --- 6. 순환 의존성 감지 ---
    println("--- 6. 순환 의존성 감지 ---")

    try {
        val circularModule = module("circular") {
            single<ServiceA> { ServiceA(get()) }
            single<ServiceB> { ServiceB(get()) }
        }

        val circularContainer = DIContainer()
        circularContainer.loadModules(circularModule)
        circularContainer.get<ServiceA>()
    } catch (e: CircularDependencyException) {
        println("Caught: ${e.message}")
    }
    println()

    // --- 7. 테스트 환경 ---
    println("--- 7. 테스트 환경 설정 ---")
    DI.stop()

    // 테스트용 모듈로 재시작
    val testModules = createTestModules() + listOf(
        module("test-repository") {
            single<UserRepository> {
                UserRepositoryImpl(get(), get())
            }
        },
        module("test-user-service") {
            single { UserService(get(), get(), get()) }
        }
    )

    DI.start {
        modules(*testModules.toTypedArray())
    }

    // Mock 사용 확인
    val mockEmail: EmailService = DI.get()
    val testUserService: UserService = DI.get()

    testUserService.registerUser("TestUser", "test@example.com")

    if (mockEmail is MockEmailService) {
        println("Sent emails: ${mockEmail.sentEmails.size}")
        mockEmail.sentEmails.forEach { (to, subject, _) ->
            println("  - To: $to, Subject: $subject")
        }
    }
    println()

    // --- 8. 종료 ---
    println("--- 8. 컨테이너 종료 ---")
    DI.stop()

    println("\n=== DI Container 핵심 원칙 ===")
    println("1. 모듈화: 관련 의존성을 모듈로 그룹화")
    println("2. 생명주기: Singleton, Factory, Scoped 관리")
    println("3. Qualifier: 같은 타입의 다른 구현체 구분")
    println("4. 순환 감지: 순환 의존성 자동 감지")
    println("5. 환경 분리: 개발/테스트/프로덕션 모듈 분리")
    println("6. 테스트 용이: Mock 주입으로 단위 테스트 가능")
    println("7. 자동 정리: AutoCloseable 자동 close()")
}

// 순환 의존성 테스트용
class ServiceA(val b: ServiceB)
class ServiceB(val a: ServiceA)
