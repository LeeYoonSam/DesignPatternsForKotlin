package structural.di.userservice

// 문제가 있는 코드: 강한 결합도
class Problem {
    // 데이터베이스 접근 클래스
    class UserDatabase {
        private val users = mutableMapOf<Long, User>()

        init {
            // 초기 데이터 생성
            users[1] = User(1, "Albert Lee", "albert@test.com")
            users[2] = User(2, "Jane Smith", "jane@example.com")
        }

        fun getUser(id: Long): User? {
            println("UserDatabase: Getting user with ID $id from database")
            return users[id]
        }

        fun saveUser(user: User) {
            println("UserDatabase: Saving user ${user.name} to database")
            users[user.id] = user
        }

        fun getAllUsers(): List<User> {
            println("UserDatabase: Getting all users from database")
            return users.values.toList()
        }
    }

    // 이메일 서비스
    class EmailService {
        fun sendEmail(to: String, subject: String, body: String) {
            println("EmailService: Sending email to $to")
            println("  Subject: $subject")
            println("  Body: $body")
        }
    }

    // 문제가 있는 사용자 서비스 - 강한 결합
    class UserService {
        // UserService가 직접 의존성을 생성함 (강한 결합)
        private val userDatabase = UserDatabase()
        private val emailService = EmailService()

        fun registerUser(name: String, email: String): User {
            // 새 사용자 ID 생성 (간단한 예제를 위한 로직)
            val id = System.currentTimeMillis()
            val user = User(id, name, email)

            // 사용자 저장
            userDatabase.saveUser(user)

            // 환영 이메일 전송
            emailService.sendEmail(
                email,
                "Welcome to our service!",
                "Hi $name, thank you for registering with us."
            )

            return user
        }

        fun getUser(id: Long): User? {
            return userDatabase.getUser(id)
        }

        fun getAllUsers(): List<User> {
            return userDatabase.getAllUsers()
        }
    }

    // 로그인 관리자 (UserService에 의존)
    class AuthManager {
        // 직접 UserService 인스턴스 생성 (강한 결합)
        private val userService = UserService()

        fun login(id: Long, password: String): Boolean {
            val user = userService.getUser(id)
            // 실제로는 비밀번호 검증 로직이 필요하지만, 예제에서는 단순화
            return user != null
        }
    }
}

fun main() {
    // 문제점을 보여주는 코드
    println("=== 문제가 있는 코드 실행 ===")

    val userService = Problem.UserService()
    val authManager = Problem.AuthManager()

    // 새 사용자 등록
    val newUser = userService.registerUser("Alice Johnson", "alice@example.com")
    println("Registered user: $newUser")

    // 로그인 시도
    val loginSuccess = authManager.login(newUser.id, "password")
    println("Login success: $loginSuccess") // 항상 true를 반환 (테스트 어려움)

    // 모든 사용자 출력
    println("All users: ${userService.getAllUsers()}")

    // 문제점:
    // 1. 강한 결합: UserService는 UserDatabase와 EmailService의 구체적인 구현에 의존
    // 2. 테스트 어려움: 실제 의존성을 모의 객체로 대체할 수 없음
    // 3. 유연성 부족: 다른 데이터베이스나 이메일 서비스로 교체 어려움
    // 4. 코드 재사용성 저하: 동일한 로직을 다른 환경에서 사용하기 어려움
}