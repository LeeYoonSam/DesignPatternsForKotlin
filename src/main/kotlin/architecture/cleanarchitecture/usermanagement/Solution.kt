package architecture.cleanarchitecture.usermanagement

import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 관리 시스템 - Clean Architecture 적용
 *
 * Clean Architecture 레이어:
 * 1. Domain (Enterprise Business Rules) - 가장 안쪽
 *    - Entity, Value Object, Domain Service
 * 2. Use Cases (Application Business Rules)
 *    - Use Case (Interactor), Input/Output Port
 * 3. Interface Adapters
 *    - Controller, Presenter, Gateway Implementation
 * 4. Frameworks & Drivers - 가장 바깥쪽
 *    - DB, Web Framework, UI, External Services
 *
 * 의존성 규칙: 바깥쪽 레이어만 안쪽 레이어에 의존
 */
class Solution {

    // ============================================================
    // LAYER 1: DOMAIN (Enterprise Business Rules)
    // - 가장 핵심적인 비즈니스 규칙
    // - 외부 의존성 없음
    // - 프레임워크 독립적
    // ============================================================

    object Domain {

        // === Value Objects ===

        @JvmInline
        value class UserId(val value: String) {
            companion object {
                fun generate() = UserId(UUID.randomUUID().toString())
            }
        }

        @JvmInline
        value class Email private constructor(val value: String) {
            companion object {
                private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

                fun create(value: String): Result<Email> {
                    return if (EMAIL_REGEX.matches(value)) {
                        Result.success(Email(value.lowercase()))
                    } else {
                        Result.failure(InvalidEmailException(value))
                    }
                }
            }
        }

        @JvmInline
        value class Password private constructor(val hashedValue: String) {
            companion object {
                fun fromHashed(hashedValue: String) = Password(hashedValue)
            }
        }

        @JvmInline
        value class UserName private constructor(val value: String) {
            companion object {
                fun create(value: String): Result<UserName> {
                    return when {
                        value.isBlank() -> Result.failure(InvalidUserNameException("이름은 비어있을 수 없습니다"))
                        value.length < 2 -> Result.failure(InvalidUserNameException("이름은 2자 이상이어야 합니다"))
                        value.length > 50 -> Result.failure(InvalidUserNameException("이름은 50자 이하여야 합니다"))
                        else -> Result.success(UserName(value.trim()))
                    }
                }
            }
        }

        // === Entity ===

        enum class UserRole { USER, ADMIN, MODERATOR }

        class User private constructor(
            val id: UserId,
            private var _name: UserName,
            private var _email: Email,
            private var _password: Password,
            private var _role: UserRole,
            val createdAt: LocalDateTime,
            private var _updatedAt: LocalDateTime
        ) {
            val name: UserName get() = _name
            val email: Email get() = _email
            val password: Password get() = _password
            val role: UserRole get() = _role
            val updatedAt: LocalDateTime get() = _updatedAt

            fun changeName(newName: UserName) {
                _name = newName
                _updatedAt = LocalDateTime.now()
            }

            fun changeEmail(newEmail: Email) {
                _email = newEmail
                _updatedAt = LocalDateTime.now()
            }

            fun changePassword(newPassword: Password) {
                _password = newPassword
                _updatedAt = LocalDateTime.now()
            }

            fun promoteToAdmin() {
                _role = UserRole.ADMIN
                _updatedAt = LocalDateTime.now()
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is User) return false
                return id == other.id
            }

            override fun hashCode() = id.hashCode()

            companion object {
                fun create(
                    name: UserName,
                    email: Email,
                    password: Password,
                    role: UserRole = UserRole.USER
                ): User {
                    return User(
                        id = UserId.generate(),
                        _name = name,
                        _email = email,
                        _password = password,
                        _role = role,
                        createdAt = LocalDateTime.now(),
                        _updatedAt = LocalDateTime.now()
                    )
                }

                // 재구성용 (Repository에서 사용)
                fun reconstitute(
                    id: UserId,
                    name: UserName,
                    email: Email,
                    password: Password,
                    role: UserRole,
                    createdAt: LocalDateTime,
                    updatedAt: LocalDateTime
                ): User {
                    return User(id, name, email, password, role, createdAt, updatedAt)
                }
            }
        }

        // === Domain Exceptions ===

        open class DomainException(message: String) : Exception(message)
        class InvalidEmailException(email: String) : DomainException("유효하지 않은 이메일: $email")
        class InvalidUserNameException(message: String) : DomainException(message)
        class UserNotFoundException(id: UserId) : DomainException("사용자를 찾을 수 없습니다: ${id.value}")
        class DuplicateEmailException(email: Email) : DomainException("이미 존재하는 이메일: ${email.value}")
        class InvalidPasswordException(message: String) : DomainException(message)

        // === Domain Service (Port) ===

        interface PasswordHasher {
            fun hash(rawPassword: String): Password
            fun verify(rawPassword: String, hashedPassword: Password): Boolean
        }
    }

    // ============================================================
    // LAYER 2: USE CASES (Application Business Rules)
    // - 애플리케이션 특화 비즈니스 규칙
    // - Use Case (Interactor) 구현
    // - Input/Output Port 정의
    // ============================================================

    object UseCases {

        // === Input Ports (Use Case Interfaces) ===

        interface RegisterUserUseCase {
            fun execute(input: RegisterUserInput): RegisterUserOutput
        }

        interface GetUserUseCase {
            fun execute(input: GetUserInput): GetUserOutput
        }

        interface UpdateProfileUseCase {
            fun execute(input: UpdateProfileInput): UpdateProfileOutput
        }

        interface ChangePasswordUseCase {
            fun execute(input: ChangePasswordInput): ChangePasswordOutput
        }

        interface DeleteUserUseCase {
            fun execute(input: DeleteUserInput): DeleteUserOutput
        }

        // === Input/Output DTOs ===

        data class RegisterUserInput(
            val name: String,
            val email: String,
            val password: String
        )

        data class RegisterUserOutput(
            val userId: String,
            val name: String,
            val email: String,
            val createdAt: LocalDateTime
        )

        data class GetUserInput(val userId: String)

        data class GetUserOutput(
            val userId: String,
            val name: String,
            val email: String,
            val role: String,
            val createdAt: LocalDateTime
        )

        data class UpdateProfileInput(
            val userId: String,
            val name: String,
            val email: String
        )

        data class UpdateProfileOutput(
            val userId: String,
            val name: String,
            val email: String,
            val updatedAt: LocalDateTime
        )

        data class ChangePasswordInput(
            val userId: String,
            val currentPassword: String,
            val newPassword: String
        )

        data class ChangePasswordOutput(
            val success: Boolean,
            val message: String
        )

        data class DeleteUserInput(val userId: String)

        data class DeleteUserOutput(
            val success: Boolean,
            val message: String
        )

        // === Output Ports (Repository & External Service Interfaces) ===

        interface UserRepository {
            fun save(user: Domain.User): Domain.User
            fun findById(id: Domain.UserId): Domain.User?
            fun findByEmail(email: Domain.Email): Domain.User?
            fun existsByEmail(email: Domain.Email): Boolean
            fun delete(user: Domain.User)
            fun findAll(): List<Domain.User>
        }

        interface NotificationService {
            fun sendWelcomeNotification(email: String, name: String)
            fun sendPasswordChangedNotification(email: String)
            fun sendAccountDeletedNotification(email: String)
        }

        // === Use Case Implementations (Interactors) ===

        class RegisterUserInteractor(
            private val userRepository: UserRepository,
            private val passwordHasher: Domain.PasswordHasher,
            private val notificationService: NotificationService
        ) : RegisterUserUseCase {

            override fun execute(input: RegisterUserInput): RegisterUserOutput {
                // 1. 입력값을 도메인 객체로 변환 및 검증
                val name = Domain.UserName.create(input.name).getOrThrow()
                val email = Domain.Email.create(input.email).getOrThrow()

                // 2. 비밀번호 검증
                validatePassword(input.password)

                // 3. 이메일 중복 확인
                if (userRepository.existsByEmail(email)) {
                    throw Domain.DuplicateEmailException(email)
                }

                // 4. 비밀번호 해싱
                val hashedPassword = passwordHasher.hash(input.password)

                // 5. 사용자 생성
                val user = Domain.User.create(name, email, hashedPassword)

                // 6. 저장
                val savedUser = userRepository.save(user)

                // 7. 알림 발송
                notificationService.sendWelcomeNotification(email.value, name.value)

                // 8. 출력 DTO 반환
                return RegisterUserOutput(
                    userId = savedUser.id.value,
                    name = savedUser.name.value,
                    email = savedUser.email.value,
                    createdAt = savedUser.createdAt
                )
            }

            private fun validatePassword(password: String) {
                if (password.length < 8) {
                    throw Domain.InvalidPasswordException("비밀번호는 8자 이상이어야 합니다")
                }
                if (!password.any { it.isDigit() }) {
                    throw Domain.InvalidPasswordException("비밀번호는 숫자를 포함해야 합니다")
                }
            }
        }

        class GetUserInteractor(
            private val userRepository: UserRepository
        ) : GetUserUseCase {

            override fun execute(input: GetUserInput): GetUserOutput {
                val userId = Domain.UserId(input.userId)
                val user = userRepository.findById(userId)
                    ?: throw Domain.UserNotFoundException(userId)

                return GetUserOutput(
                    userId = user.id.value,
                    name = user.name.value,
                    email = user.email.value,
                    role = user.role.name,
                    createdAt = user.createdAt
                )
            }
        }

        class UpdateProfileInteractor(
            private val userRepository: UserRepository
        ) : UpdateProfileUseCase {

            override fun execute(input: UpdateProfileInput): UpdateProfileOutput {
                val userId = Domain.UserId(input.userId)
                val user = userRepository.findById(userId)
                    ?: throw Domain.UserNotFoundException(userId)

                val newName = Domain.UserName.create(input.name).getOrThrow()
                val newEmail = Domain.Email.create(input.email).getOrThrow()

                // 이메일 변경 시 중복 확인
                if (newEmail != user.email && userRepository.existsByEmail(newEmail)) {
                    throw Domain.DuplicateEmailException(newEmail)
                }

                user.changeName(newName)
                user.changeEmail(newEmail)

                val savedUser = userRepository.save(user)

                return UpdateProfileOutput(
                    userId = savedUser.id.value,
                    name = savedUser.name.value,
                    email = savedUser.email.value,
                    updatedAt = savedUser.updatedAt
                )
            }
        }

        class ChangePasswordInteractor(
            private val userRepository: UserRepository,
            private val passwordHasher: Domain.PasswordHasher,
            private val notificationService: NotificationService
        ) : ChangePasswordUseCase {

            override fun execute(input: ChangePasswordInput): ChangePasswordOutput {
                val userId = Domain.UserId(input.userId)
                val user = userRepository.findById(userId)
                    ?: throw Domain.UserNotFoundException(userId)

                // 현재 비밀번호 확인
                if (!passwordHasher.verify(input.currentPassword, user.password)) {
                    throw Domain.InvalidPasswordException("현재 비밀번호가 일치하지 않습니다")
                }

                // 새 비밀번호 검증
                if (input.newPassword.length < 8) {
                    throw Domain.InvalidPasswordException("새 비밀번호는 8자 이상이어야 합니다")
                }

                // 비밀번호 변경
                val newHashedPassword = passwordHasher.hash(input.newPassword)
                user.changePassword(newHashedPassword)

                userRepository.save(user)

                notificationService.sendPasswordChangedNotification(user.email.value)

                return ChangePasswordOutput(
                    success = true,
                    message = "비밀번호가 변경되었습니다"
                )
            }
        }

        class DeleteUserInteractor(
            private val userRepository: UserRepository,
            private val notificationService: NotificationService
        ) : DeleteUserUseCase {

            override fun execute(input: DeleteUserInput): DeleteUserOutput {
                val userId = Domain.UserId(input.userId)
                val user = userRepository.findById(userId)
                    ?: throw Domain.UserNotFoundException(userId)

                val email = user.email.value

                userRepository.delete(user)

                notificationService.sendAccountDeletedNotification(email)

                return DeleteUserOutput(
                    success = true,
                    message = "계정이 삭제되었습니다"
                )
            }
        }
    }

    // ============================================================
    // LAYER 3: INTERFACE ADAPTERS
    // - Controller, Presenter, Gateway
    // - 외부 형식과 내부 형식 간 변환
    // ============================================================

    object InterfaceAdapters {

        // === Controller (Web → Use Case) ===

        class UserController(
            private val registerUserUseCase: UseCases.RegisterUserUseCase,
            private val getUserUseCase: UseCases.GetUserUseCase,
            private val updateProfileUseCase: UseCases.UpdateProfileUseCase,
            private val changePasswordUseCase: UseCases.ChangePasswordUseCase,
            private val deleteUserUseCase: UseCases.DeleteUserUseCase
        ) {
            fun register(request: RegisterRequest): ApiResponse<UserResponse> {
                return try {
                    val input = UseCases.RegisterUserInput(
                        name = request.name,
                        email = request.email,
                        password = request.password
                    )
                    val output = registerUserUseCase.execute(input)
                    ApiResponse.success(UserResponse.from(output))
                } catch (e: Domain.DomainException) {
                    ApiResponse.error(e.message ?: "등록 실패")
                }
            }

            fun getUser(userId: String): ApiResponse<UserResponse> {
                return try {
                    val output = getUserUseCase.execute(UseCases.GetUserInput(userId))
                    ApiResponse.success(UserResponse.fromGetOutput(output))
                } catch (e: Domain.DomainException) {
                    ApiResponse.error(e.message ?: "조회 실패")
                }
            }

            fun updateProfile(userId: String, request: UpdateProfileRequest): ApiResponse<UserResponse> {
                return try {
                    val input = UseCases.UpdateProfileInput(
                        userId = userId,
                        name = request.name,
                        email = request.email
                    )
                    val output = updateProfileUseCase.execute(input)
                    ApiResponse.success(UserResponse.fromUpdateOutput(output))
                } catch (e: Domain.DomainException) {
                    ApiResponse.error(e.message ?: "수정 실패")
                }
            }

            fun changePassword(userId: String, request: ChangePasswordRequest): ApiResponse<MessageResponse> {
                return try {
                    val input = UseCases.ChangePasswordInput(
                        userId = userId,
                        currentPassword = request.currentPassword,
                        newPassword = request.newPassword
                    )
                    val output = changePasswordUseCase.execute(input)
                    ApiResponse.success(MessageResponse(output.message))
                } catch (e: Domain.DomainException) {
                    ApiResponse.error(e.message ?: "비밀번호 변경 실패")
                }
            }

            fun deleteUser(userId: String): ApiResponse<MessageResponse> {
                return try {
                    val output = deleteUserUseCase.execute(UseCases.DeleteUserInput(userId))
                    ApiResponse.success(MessageResponse(output.message))
                } catch (e: Domain.DomainException) {
                    ApiResponse.error(e.message ?: "삭제 실패")
                }
            }
        }

        // === Request/Response DTOs (API 형식) ===

        data class RegisterRequest(
            val name: String,
            val email: String,
            val password: String
        )

        data class UpdateProfileRequest(
            val name: String,
            val email: String
        )

        data class ChangePasswordRequest(
            val currentPassword: String,
            val newPassword: String
        )

        data class UserResponse(
            val id: String,
            val name: String,
            val email: String,
            val role: String? = null,
            val createdAt: String
        ) {
            companion object {
                fun from(output: UseCases.RegisterUserOutput) = UserResponse(
                    id = output.userId,
                    name = output.name,
                    email = output.email,
                    createdAt = output.createdAt.toString()
                )

                fun fromGetOutput(output: UseCases.GetUserOutput) = UserResponse(
                    id = output.userId,
                    name = output.name,
                    email = output.email,
                    role = output.role,
                    createdAt = output.createdAt.toString()
                )

                fun fromUpdateOutput(output: UseCases.UpdateProfileOutput) = UserResponse(
                    id = output.userId,
                    name = output.name,
                    email = output.email,
                    createdAt = output.updatedAt.toString()
                )
            }
        }

        data class MessageResponse(val message: String)

        data class ApiResponse<T>(
            val success: Boolean,
            val data: T? = null,
            val error: String? = null
        ) {
            companion object {
                fun <T> success(data: T) = ApiResponse(success = true, data = data)
                fun <T> error(message: String) = ApiResponse<T>(success = false, error = message)
            }
        }

        // === Gateway (Use Case → External Systems) ===

        // Repository 구현은 Frameworks 레이어에서
        // 여기서는 데이터 변환 로직만 포함할 수 있음
    }

    // ============================================================
    // LAYER 4: FRAMEWORKS & DRIVERS
    // - DB, Web Framework, External Services
    // - 가장 바깥쪽 레이어
    // ============================================================

    object Frameworks {

        // === Database (In-Memory 구현) ===

        // DB용 데이터 모델 (JPA Entity 역할)
        data class UserDbEntity(
            val id: String,
            var name: String,
            var email: String,
            var passwordHash: String,
            var role: String,
            val createdAt: String,
            var updatedAt: String
        )

        class InMemoryUserRepository : UseCases.UserRepository {
            private val storage = mutableMapOf<String, UserDbEntity>()

            override fun save(user: Domain.User): Domain.User {
                val entity = UserDbEntity(
                    id = user.id.value,
                    name = user.name.value,
                    email = user.email.value,
                    passwordHash = user.password.hashedValue,
                    role = user.role.name,
                    createdAt = user.createdAt.toString(),
                    updatedAt = user.updatedAt.toString()
                )
                storage[entity.id] = entity
                return user
            }

            override fun findById(id: Domain.UserId): Domain.User? {
                val entity = storage[id.value] ?: return null
                return toDomain(entity)
            }

            override fun findByEmail(email: Domain.Email): Domain.User? {
                val entity = storage.values.find { it.email == email.value } ?: return null
                return toDomain(entity)
            }

            override fun existsByEmail(email: Domain.Email): Boolean {
                return storage.values.any { it.email == email.value }
            }

            override fun delete(user: Domain.User) {
                storage.remove(user.id.value)
            }

            override fun findAll(): List<Domain.User> {
                return storage.values.map { toDomain(it) }
            }

            private fun toDomain(entity: UserDbEntity): Domain.User {
                return Domain.User.reconstitute(
                    id = Domain.UserId(entity.id),
                    name = Domain.UserName.create(entity.name).getOrThrow(),
                    email = Domain.Email.create(entity.email).getOrThrow(),
                    password = Domain.Password.fromHashed(entity.passwordHash),
                    role = Domain.UserRole.valueOf(entity.role),
                    createdAt = LocalDateTime.parse(entity.createdAt),
                    updatedAt = LocalDateTime.parse(entity.updatedAt)
                )
            }
        }

        // === External Services ===

        class BcryptPasswordHasher : Domain.PasswordHasher {
            override fun hash(rawPassword: String): Domain.Password {
                // 실제로는 BCrypt 사용
                val hashed = "bcrypt_" + rawPassword.hashCode().toString(16)
                return Domain.Password.fromHashed(hashed)
            }

            override fun verify(rawPassword: String, hashedPassword: Domain.Password): Boolean {
                val expectedHash = "bcrypt_" + rawPassword.hashCode().toString(16)
                return hashedPassword.hashedValue == expectedHash
            }
        }

        class EmailNotificationService : UseCases.NotificationService {
            override fun sendWelcomeNotification(email: String, name: String) {
                println("  [Email] Welcome! $name ($email)")
            }

            override fun sendPasswordChangedNotification(email: String) {
                println("  [Email] Password changed notification sent to $email")
            }

            override fun sendAccountDeletedNotification(email: String) {
                println("  [Email] Account deleted notification sent to $email")
            }
        }

        // === Dependency Injection Container (Simple) ===

        class Container {
            // Frameworks
            val userRepository: UseCases.UserRepository = InMemoryUserRepository()
            val passwordHasher: Domain.PasswordHasher = BcryptPasswordHasher()
            val notificationService: UseCases.NotificationService = EmailNotificationService()

            // Use Cases
            val registerUserUseCase: UseCases.RegisterUserUseCase = UseCases.RegisterUserInteractor(
                userRepository, passwordHasher, notificationService
            )
            val getUserUseCase: UseCases.GetUserUseCase = UseCases.GetUserInteractor(userRepository)
            val updateProfileUseCase: UseCases.UpdateProfileUseCase = UseCases.UpdateProfileInteractor(userRepository)
            val changePasswordUseCase: UseCases.ChangePasswordUseCase = UseCases.ChangePasswordInteractor(
                userRepository, passwordHasher, notificationService
            )
            val deleteUserUseCase: UseCases.DeleteUserUseCase = UseCases.DeleteUserInteractor(
                userRepository, notificationService
            )

            // Controllers
            val userController = InterfaceAdapters.UserController(
                registerUserUseCase,
                getUserUseCase,
                updateProfileUseCase,
                changePasswordUseCase,
                deleteUserUseCase
            )
        }
    }
}

fun main() {
    println("=== Clean Architecture 적용 데모 ===")
    println()

    // DI Container로 의존성 구성
    val container = Solution.Frameworks.Container()
    val controller = container.userController

    // 1. 사용자 등록
    println("--- 1. 사용자 등록 ---")
    val registerResponse = controller.register(
        Solution.InterfaceAdapters.RegisterRequest(
            name = "홍길동",
            email = "hong@example.com",
            password = "password123"
        )
    )
    println("응답: $registerResponse")
    println("→ 비밀번호가 응답에 포함되지 않음!")
    println()

    val userId = registerResponse.data?.id ?: return

    // 2. 사용자 조회
    println("--- 2. 사용자 조회 ---")
    val getUserResponse = controller.getUser(userId)
    println("응답: $getUserResponse")
    println()

    // 3. 프로필 수정
    println("--- 3. 프로필 수정 ---")
    val updateResponse = controller.updateProfile(
        userId,
        Solution.InterfaceAdapters.UpdateProfileRequest(
            name = "홍길동(수정)",
            email = "hong.updated@example.com"
        )
    )
    println("응답: $updateResponse")
    println()

    // 4. 비밀번호 변경
    println("--- 4. 비밀번호 변경 ---")
    val passwordResponse = controller.changePassword(
        userId,
        Solution.InterfaceAdapters.ChangePasswordRequest(
            currentPassword = "password123",
            newPassword = "newpassword456"
        )
    )
    println("응답: $passwordResponse")
    println()

    // 5. 유효성 검증 테스트
    println("--- 5. 유효성 검증 (잘못된 이메일) ---")
    val invalidResponse = controller.register(
        Solution.InterfaceAdapters.RegisterRequest(
            name = "테스트",
            email = "invalid-email",
            password = "password123"
        )
    )
    println("응답: $invalidResponse")
    println()

    // 6. 중복 이메일 테스트
    println("--- 6. 중복 이메일 테스트 ---")
    val duplicateResponse = controller.register(
        Solution.InterfaceAdapters.RegisterRequest(
            name = "다른사용자",
            email = "hong.updated@example.com",
            password = "password123"
        )
    )
    println("응답: $duplicateResponse")
    println()

    println("=== Clean Architecture 장점 ===")
    println("1. 의존성 규칙: 바깥 → 안쪽 방향으로만 의존")
    println("2. Domain 레이어: 프레임워크 독립적, 순수 비즈니스 규칙")
    println("3. Use Case 레이어: 애플리케이션 로직 캡슐화")
    println("4. Interface Adapters: 외부/내부 형식 변환")
    println("5. Frameworks: 인프라 구현 (교체 용이)")
    println("6. 테스트 용이: 각 레이어 독립적 테스트 가능")
    println("7. 보안: 도메인 모델이 API에 직접 노출되지 않음")
    println()

    println("=== 레이어별 의존성 ===")
    println("Domain        → (의존성 없음)")
    println("Use Cases     → Domain")
    println("Adapters      → Use Cases, Domain")
    println("Frameworks    → Adapters, Use Cases, Domain")
}
