package structural.di.userservice

class Solution {
    interface UserRepository {
        fun getUser(id: Long): User?
        fun saveUser(user: User)
        fun getAllUsers(): List<User>
    }

    // 인터페이스 구현 - 실제 데이터베이스
    class UserDatabaseRepository : UserRepository {
        private val users = mutableMapOf<Long, User>()

        init {
            // 초기 데이터 생성
            users[1] = User(1, "John Doe", "john@example.com")
            users[2] = User(2, "Jane Smith", "jane@example.com")
        }

        override fun getUser(id: Long): User? {
            println("DatabaseRepository: Getting user with ID $id from database")
            return users[id]
        }

        override fun saveUser(user: User) {
            println("DatabaseRepository: Saving user ${user.name} to database")
            users[user.id] = user
        }

        override fun getAllUsers(): List<User> {
            println("DatabaseRepository: Getting all users from database")
            return users.values.toList()
        }
    }

    // 테스트용 구현 - 메모리 기반 저장소
    class InMemoryUserRepository : UserRepository {
        private val users = mutableMapOf<Long, User>()

        override fun getUser(id: Long): User? {
            println("InMemoryRepository: Getting user with ID $id")
            return users[id]
        }

        override fun saveUser(user: User) {
            println("InMemoryRepository: Saving user ${user.name}")
            users[user.id] = user
        }

        override fun getAllUsers(): List<User> {
            println("InMemoryRepository: Getting all users")
            return users.values.toList()
        }
    }

    // 인터페이스 정의 - 이메일 서비스
    interface EmailSender {
        fun sendEmail(to: String, subject: String, body: String)
    }

    // 인터페이스 구현 - 실제 이메일 서비스
    class SmtpEmailService : EmailSender {
        override fun sendEmail(to: String, subject: String, body: String) {
            println("SMTP EmailService: Sending email to $to")
            println("  Subject: $subject")
            println("  Body: $body")
        }
    }

    // 테스트용 구현 - 이메일 전송 로깅만 수행
    class MockEmailService : EmailSender {
        val sentEmails = mutableListOf<Triple<String, String, String>>()

        override fun sendEmail(to: String, subject: String, body: String) {
            println("Mock EmailService: Logging email to $to")
            sentEmails.add(Triple(to, subject, body))
        }
    }

    // 개선된 사용자 서비스 - 의존성 주입 사용
    class ImprovedUserService(
        private val userRepository: UserRepository, // 생성자 주입
        private val emailSender: EmailSender,       // 생성자 주입
    ) {
        fun registerUser(name: String, email: String): User {
            // 새 사용자 ID 생성
            val id = System.currentTimeMillis()
            val user = User(id, name, email)

            // 사용자 저장
            userRepository.saveUser(user)

            // 환영 이메일 전송
            emailSender.sendEmail(
                to = email,
                subject = "Welcome to our service!",
                body = "Hi $name, thank you for registering with us."
            )

            return user
        }

        fun getUser(id: Long): User? {
            return userRepository.getUser(id)
        }

        fun getAllUsers(): List<User> {
            return userRepository.getAllUsers()
        }
    }

    // 개선된 로그인 관리자 - 의존성 주입 사용
    class ImprovedAuthManager(private val userService: ImprovedUserService) { // 생성자 주입
        fun login(id: Long, password: String): Boolean {
            val user = userService.getUser(id)
            // 실제로는 비밀번호 검증 로직이 필요하지만, 예제에서는 단순화
            return user != null
        }
    }

    // 간단한 의존성 컨테이너 (실제 DI 프레임워크는 더 복잡)
    class ServiceContainer {
        // 싱글톤 인스턴스 저장
        private val instances = mutableMapOf<Class<*>, Any>()

        fun <T: Any> register(clazz: Class<*>, instance: T) {
            instances[clazz] = instance
        }

        @Suppress("UNCHECKED_CAST")
        fun <T: Any> get(clazz: Class<T>): T {
            return instances[clazz] as? T ?: throw IllegalArgumentException("No instance registered for ${clazz.name}")
        }
    }
}

fun main() {
    println("\n=== 의존성 주입 패턴 적용 코드 실행 ===")

    // 1. 실제 환경을 위한 셋업
    println("\n--- 실제 환경 셋업 ---")
    val productionContainer = Solution.ServiceContainer()

    // 실제 구현체 등록
    val realRepository = Solution.UserDatabaseRepository()
    val realEmailService = Solution.SmtpEmailService()

    productionContainer.register(Solution.UserRepository::class.java, realRepository)
    productionContainer.register(Solution.EmailSender::class.java, realEmailService)

    // UserService 생성 (의존성 주입)
    val userService = Solution.ImprovedUserService(
        productionContainer.get(Solution.UserRepository::class.java),
        productionContainer.get(Solution.EmailSender::class.java)
    )

    // AuthManager 생성 (의존성 주입)
    val authManager = Solution.ImprovedAuthManager(userService)

    // 새 사용자 등록
    val newUser = userService.registerUser("Alice Johnson", "alice@example.com")
    println("Registered user: $newUser")

    // 로그인 시도
    val loginSuccess = authManager.login(newUser.id, "password")
    println("Login success: $loginSuccess")

    // 모든 사용자 출력
    println("All users: ${userService.getAllUsers()}")

    // 2. 테스트 환경을 위한 셋업
    println("\n--- 테스트 환경 셋업 ---")
    val testContainer = Solution.ServiceContainer()

    // 테스트용 모의 객체 등록
    val testRepository = Solution.InMemoryUserRepository()
    val testEmailService = Solution.MockEmailService()

    testContainer.register(Solution.UserRepository::class.java, testRepository)
    testContainer.register(Solution.EmailSender::class.java, testEmailService)

    // 테스트용 UserService 생성 (모의 객체 주입)
    val testUserService = Solution.ImprovedUserService(
        testContainer.get(Solution.UserRepository::class.java),
        testContainer.get(Solution.EmailSender::class.java)
    )

    // 테스트용 AuthManager 생성
    val testAuthManager = Solution.ImprovedAuthManager(testUserService)

    // 테스트 코드
    val testUser = testUserService.registerUser("Test User", "test@example.com")
    println("Test user registered: $testUser")
    println("Email sent count: ${(testEmailService.sentEmails.size)}")
    println("Login test result: ${testAuthManager.login(testUser.id, "test")}")

    // 장점:
    // 1. 느슨한 결합: 구체적인 구현체가 아닌 인터페이스에 의존
    // 2. 테스트 용이성: 실제 구현체를 모의 객체로 쉽게 대체 가능
    // 3. 유연성: 다른 구현체로 쉽게 교체 가능
    // 4. 코드 재사용성 향상: 동일한 로직을 다른 환경에서 재사용 가능
}