package architecture.cleanarchitecture.usermanagement

import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 관리 시스템 - Clean Architecture 적용 전
 *
 * 문제점:
 * - 모든 코드가 하나의 레이어에 혼재
 * - 비즈니스 로직이 프레임워크/인프라에 강하게 결합
 * - 데이터베이스 엔티티와 도메인 모델이 혼합
 * - 테스트하기 어려움 (외부 의존성 분리 불가)
 * - 프레임워크 변경 시 전체 코드 수정 필요
 * - 의존성 방향이 불명확
 */
class Problem {

    // 문제 1: DB 엔티티와 도메인 모델이 혼합됨
    // JPA/ORM 어노테이션이 도메인 모델에 직접 적용
    // @Entity
    // @Table(name = "users")
    data class User(
        // @Id @GeneratedValue
        val id: Long? = null,
        // @Column(nullable = false)
        var name: String,
        // @Column(unique = true, nullable = false)
        var email: String,
        // @Column(nullable = false)
        var password: String,  // 평문 저장 위험
        // @Enumerated(EnumType.STRING)
        var role: Role = Role.USER,
        // @Column(name = "created_at")
        val createdAt: LocalDateTime = LocalDateTime.now(),
        // @Column(name = "updated_at")
        var updatedAt: LocalDateTime = LocalDateTime.now()
    ) {
        enum class Role { USER, ADMIN, MODERATOR }
    }

    // 문제 2: 서비스가 인프라에 직접 의존
    // Spring의 @Service, @Transactional 등이 비즈니스 로직과 결합
    // @Service
    // @Transactional
    class UserService(
        // @Autowired
        private val userRepository: UserRepository,
        // @Autowired
        private val emailService: EmailService,
        // @Autowired
        private val passwordEncoder: PasswordEncoder
    ) {
        // 문제 3: 비즈니스 로직과 인프라 로직이 혼합
        fun registerUser(name: String, email: String, password: String): User {
            // 유효성 검증 - 어디서 해야 하나?
            if (name.isBlank()) {
                throw IllegalArgumentException("이름은 필수입니다")
            }
            if (!email.contains("@")) {
                throw IllegalArgumentException("유효하지 않은 이메일입니다")
            }
            if (password.length < 8) {
                throw IllegalArgumentException("비밀번호는 8자 이상이어야 합니다")
            }

            // 중복 확인 - DB 직접 접근
            if (userRepository.existsByEmail(email)) {
                throw IllegalStateException("이미 존재하는 이메일입니다")
            }

            // 사용자 생성
            val user = User(
                name = name,
                email = email,
                password = passwordEncoder.encode(password)
            )

            // 저장
            val savedUser = userRepository.save(user)

            // 이메일 발송 - 인프라 로직이 비즈니스 로직과 혼합
            emailService.sendWelcomeEmail(email, name)

            return savedUser
        }

        fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
            val user = userRepository.findById(userId)
                ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다")

            if (!passwordEncoder.matches(oldPassword, user.password)) {
                throw IllegalArgumentException("현재 비밀번호가 일치하지 않습니다")
            }

            if (newPassword.length < 8) {
                throw IllegalArgumentException("새 비밀번호는 8자 이상이어야 합니다")
            }

            user.password = passwordEncoder.encode(newPassword)
            user.updatedAt = LocalDateTime.now()
            userRepository.save(user)

            // 비밀번호 변경 알림
            emailService.sendPasswordChangedEmail(user.email)
        }

        fun updateProfile(userId: Long, name: String, email: String): User {
            val user = userRepository.findById(userId)
                ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다")

            // 이메일 변경 시 중복 확인
            if (email != user.email && userRepository.existsByEmail(email)) {
                throw IllegalStateException("이미 존재하는 이메일입니다")
            }

            user.name = name
            user.email = email
            user.updatedAt = LocalDateTime.now()

            return userRepository.save(user)
        }

        fun deleteUser(userId: Long) {
            val user = userRepository.findById(userId)
                ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다")

            userRepository.delete(user)
            emailService.sendAccountDeletedEmail(user.email)
        }

        fun getUser(userId: Long): User {
            return userRepository.findById(userId)
                ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다")
        }

        fun getAllUsers(): List<User> {
            return userRepository.findAll()
        }
    }

    // 문제 4: Repository가 특정 프레임워크에 종속
    // interface UserRepository : JpaRepository<User, Long>
    interface UserRepository {
        fun save(user: User): User
        fun findById(id: Long): User?
        fun findByEmail(email: String): User?
        fun existsByEmail(email: String): Boolean
        fun delete(user: User)
        fun findAll(): List<User>
    }

    // 문제 5: 컨트롤러가 도메인 모델을 직접 반환
    // @RestController
    // @RequestMapping("/api/users")
    class UserController(
        // @Autowired
        private val userService: UserService
    ) {
        // @PostMapping
        fun register(request: RegisterRequest): User {  // 도메인 모델 직접 노출
            return userService.registerUser(
                name = request.name,
                email = request.email,
                password = request.password
            )
        }

        // @GetMapping("/{id}")
        fun getUser(id: Long): User {  // 도메인 모델 직접 노출 (비밀번호 포함!)
            return userService.getUser(id)
        }
    }

    data class RegisterRequest(
        val name: String,
        val email: String,
        val password: String
    )

    // 인프라 서비스들 (간단한 구현)
    interface PasswordEncoder {
        fun encode(password: String): String
        fun matches(rawPassword: String, encodedPassword: String): Boolean
    }

    interface EmailService {
        fun sendWelcomeEmail(email: String, name: String)
        fun sendPasswordChangedEmail(email: String)
        fun sendAccountDeletedEmail(email: String)
    }

    // 간단한 인메모리 구현
    class InMemoryUserRepository : UserRepository {
        private val users = mutableMapOf<Long, User>()
        private var sequence = 1L

        override fun save(user: User): User {
            val savedUser = if (user.id == null) {
                user.copy(id = sequence++)
            } else {
                user
            }
            users[savedUser.id!!] = savedUser
            return savedUser
        }

        override fun findById(id: Long) = users[id]
        override fun findByEmail(email: String) = users.values.find { it.email == email }
        override fun existsByEmail(email: String) = users.values.any { it.email == email }
        override fun delete(user: User) { users.remove(user.id) }
        override fun findAll() = users.values.toList()
    }

    class SimplePasswordEncoder : PasswordEncoder {
        override fun encode(password: String) = "encoded_$password"
        override fun matches(rawPassword: String, encodedPassword: String) =
            encodedPassword == "encoded_$rawPassword"
    }

    class ConsoleEmailService : EmailService {
        override fun sendWelcomeEmail(email: String, name: String) {
            println("  [Email] Welcome email sent to $email")
        }
        override fun sendPasswordChangedEmail(email: String) {
            println("  [Email] Password changed notification sent to $email")
        }
        override fun sendAccountDeletedEmail(email: String) {
            println("  [Email] Account deleted notification sent to $email")
        }
    }
}

fun main() {
    // 의존성 수동 구성 (실제로는 DI 프레임워크 사용)
    val userRepository = Problem.InMemoryUserRepository()
    val passwordEncoder = Problem.SimplePasswordEncoder()
    val emailService = Problem.ConsoleEmailService()

    val userService = Problem.UserService(userRepository, emailService, passwordEncoder)
    val userController = Problem.UserController(userService)

    println("=== Clean Architecture 적용 전 문제점 데모 ===")
    println()

    // 사용자 등록
    println("--- 사용자 등록 ---")
    val user = userController.register(
        Problem.RegisterRequest("홍길동", "hong@example.com", "password123")
    )
    println("등록된 사용자: $user")
    println()

    // 문제점: 비밀번호가 그대로 노출됨
    println("--- 문제점: API 응답에 비밀번호 노출 ---")
    val fetchedUser = userController.getUser(user.id!!)
    println("조회된 사용자: $fetchedUser")
    println("→ 비밀번호 필드가 응답에 포함됨!")
    println()

    // 비밀번호 변경
    println("--- 비밀번호 변경 ---")
    userService.changePassword(user.id!!, "password123", "newpassword456")
    println("비밀번호 변경 완료")
    println()

    println("=== 문제점 요약 ===")
    println("1. 도메인 모델(User)이 DB 엔티티 역할도 함")
    println("2. 비즈니스 로직이 프레임워크 어노테이션과 결합")
    println("3. 서비스가 인프라(DB, Email)에 직접 의존")
    println("4. 컨트롤러가 도메인 모델을 직접 반환 (비밀번호 노출)")
    println("5. 테스트 시 전체 인프라 모킹 필요")
    println("6. 프레임워크 변경 시 전체 코드 수정 필요")
    println("7. 의존성 방향이 바깥→안쪽이 아님")
}
