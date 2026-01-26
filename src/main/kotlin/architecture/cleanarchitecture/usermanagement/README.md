# Clean Architecture Pattern

## 설명
Clean Architecture는 Robert C. Martin(Uncle Bob)이 제안한 소프트웨어 아키텍처로, 관심사의 분리와 의존성 규칙을 통해 테스트 가능하고, 유지보수하기 쉬우며, 프레임워크로부터 독립적인 시스템을 구축합니다.

## 핵심 원칙: 의존성 규칙 (Dependency Rule)

**"소스 코드 의존성은 안쪽을 향해야 한다"**

```
┌─────────────────────────────────────────────────────────────┐
│                  Frameworks & Drivers                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 Interface Adapters                     │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │                  Use Cases                       │  │  │
│  │  │  ┌───────────────────────────────────────────┐  │  │  │
│  │  │  │                 Domain                     │  │  │  │
│  │  │  │           (Enterprise Rules)              │  │  │  │
│  │  │  └───────────────────────────────────────────┘  │  │  │
│  │  │              (Application Rules)                 │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  │           (Controllers, Presenters, Gateways)         │  │
│  └───────────────────────────────────────────────────────┘  │
│                    (DB, Web, UI, Devices)                    │
└─────────────────────────────────────────────────────────────┘

                    의존성 방향 ────────→ (안쪽으로)
```

## 레이어 구조

### Layer 1: Domain (Enterprise Business Rules)
가장 안쪽 레이어. 핵심 비즈니스 규칙을 포함합니다.

```kotlin
// Entity
class User private constructor(
    val id: UserId,
    private var _name: UserName,
    private var _email: Email
) {
    fun changeName(newName: UserName) { ... }
}

// Value Object
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun create(value: String): Result<Email>
    }
}

// Domain Service
interface PasswordHasher {
    fun hash(rawPassword: String): Password
}
```

**특징:**
- 외부 의존성 없음
- 프레임워크 독립적
- 가장 변경 가능성이 낮음

### Layer 2: Use Cases (Application Business Rules)
애플리케이션 특화 비즈니스 규칙을 포함합니다.

```kotlin
// Use Case (Interactor)
class RegisterUserInteractor(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher
) : RegisterUserUseCase {

    override fun execute(input: RegisterUserInput): RegisterUserOutput {
        // 1. 입력 검증
        // 2. 비즈니스 규칙 적용
        // 3. 엔티티 생성/수정
        // 4. 출력 반환
    }
}

// Input/Output Port
interface RegisterUserUseCase {
    fun execute(input: RegisterUserInput): RegisterUserOutput
}
```

**특징:**
- 도메인 레이어에만 의존
- Use Case별로 분리
- Input/Output DTO 사용

### Layer 3: Interface Adapters
외부와 내부 형식 간의 변환을 담당합니다.

```kotlin
// Controller (Web → Use Case)
class UserController(
    private val registerUserUseCase: RegisterUserUseCase
) {
    fun register(request: RegisterRequest): ApiResponse<UserResponse> {
        val input = RegisterUserInput(...)
        val output = registerUserUseCase.execute(input)
        return ApiResponse.success(UserResponse.from(output))
    }
}

// Gateway (Use Case → DB)
class JpaUserRepository : UserRepository {
    override fun save(user: User): User {
        val entity = toJpaEntity(user)
        jpaRepository.save(entity)
        return user
    }
}
```

**특징:**
- 형식 변환 담당
- Use Case와 외부 세계 연결
- Presenter, Controller, Gateway 포함

### Layer 4: Frameworks & Drivers
가장 바깥쪽 레이어. 프레임워크와 도구를 포함합니다.

```kotlin
// Database Configuration
@Configuration
class DatabaseConfig { ... }

// Web Framework
@RestController
class UserRestController(private val controller: UserController) {
    @PostMapping("/users")
    fun register(@RequestBody request: RegisterRequest) =
        controller.register(request)
}
```

**특징:**
- 프레임워크 특화 코드
- 쉽게 교체 가능
- 세부 사항 (Details)

## 의존성 역전 (Dependency Inversion)

```kotlin
// Use Case 레이어에서 인터페이스 정의
interface UserRepository {
    fun save(user: User): User
    fun findById(id: UserId): User?
}

// Frameworks 레이어에서 구현
class InMemoryUserRepository : UserRepository { ... }
class JpaUserRepository : UserRepository { ... }
class MongoUserRepository : UserRepository { ... }
```

이를 통해:
- Use Case는 구체적인 DB 기술에 의존하지 않음
- Repository 구현체 교체가 자유로움
- 테스트 시 Mock/Fake 사용 용이

## 데이터 흐름

### Request 흐름
```
HTTP Request
    ↓
[Frameworks] Web Controller (Spring MVC)
    ↓
[Adapters] UserController - Request → Input DTO 변환
    ↓
[Use Cases] RegisterUserInteractor - 비즈니스 로직 실행
    ↓
[Domain] User Entity - 도메인 규칙 적용
    ↓
[Use Cases] Output DTO 생성
    ↓
[Adapters] UserController - Output → Response 변환
    ↓
[Frameworks] Web Controller
    ↓
HTTP Response
```

### 의존성 vs 데이터 흐름
```
의존성:    Frameworks → Adapters → Use Cases → Domain
데이터:    Request → Controller → Use Case → Entity → Repository → DB
          DB → Repository → Entity → Use Case → Controller → Response
```

## Kotlin 구현 기법

### 1. Value Class로 Value Object
```kotlin
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun create(value: String): Result<Email>
    }
}
```

### 2. sealed class로 결과 타입
```kotlin
sealed class RegisterResult {
    data class Success(val user: UserResponse) : RegisterResult()
    data class ValidationError(val message: String) : RegisterResult()
    data class DuplicateEmail(val email: String) : RegisterResult()
}
```

### 3. object로 레이어 구분
```kotlin
object Domain { ... }
object UseCases { ... }
object InterfaceAdapters { ... }
object Frameworks { ... }
```

### 4. 생성자 주입
```kotlin
class RegisterUserInteractor(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val notificationService: NotificationService
) : RegisterUserUseCase
```

## 테스트 전략

### Domain 레이어 테스트
```kotlin
@Test
fun `이메일 형식이 잘못되면 생성 실패`() {
    val result = Email.create("invalid")
    assertTrue(result.isFailure)
}
```

### Use Case 테스트 (Mock 사용)
```kotlin
@Test
fun `사용자 등록 성공`() {
    val mockRepository = mockk<UserRepository>()
    every { mockRepository.existsByEmail(any()) } returns false
    every { mockRepository.save(any()) } returnsArgument 0

    val useCase = RegisterUserInteractor(mockRepository, ...)
    val result = useCase.execute(input)

    assertTrue(result.userId.isNotBlank())
}
```

### Integration 테스트
```kotlin
@Test
fun `전체 등록 플로우`() {
    val container = TestContainer()
    val response = container.userController.register(request)

    assertTrue(response.success)
}
```

## Clean Architecture vs 다른 아키텍처

| 특성 | Clean Architecture | Layered Architecture | Hexagonal |
|------|-------------------|---------------------|-----------|
| 의존성 방향 | 안쪽으로만 | 위에서 아래로 | 안쪽으로만 |
| 중심 | Domain | Data | Domain |
| 포트/어댑터 | 암시적 | 없음 | 명시적 |
| 테스트 | 매우 용이 | 어려움 | 용이 |

## 디렉토리 구조 예시

```
src/
├── domain/
│   ├── entity/
│   │   └── User.kt
│   ├── valueobject/
│   │   ├── Email.kt
│   │   └── UserId.kt
│   ├── service/
│   │   └── PasswordHasher.kt
│   └── exception/
│       └── DomainException.kt
├── usecase/
│   ├── port/
│   │   ├── input/
│   │   │   └── RegisterUserUseCase.kt
│   │   └── output/
│   │       └── UserRepository.kt
│   ├── interactor/
│   │   └── RegisterUserInteractor.kt
│   └── dto/
│       ├── RegisterUserInput.kt
│       └── RegisterUserOutput.kt
├── adapter/
│   ├── controller/
│   │   └── UserController.kt
│   ├── presenter/
│   │   └── UserPresenter.kt
│   └── gateway/
│       └── UserRepositoryImpl.kt
└── framework/
    ├── web/
    │   └── UserRestController.kt
    ├── db/
    │   └── JpaUserRepository.kt
    └── config/
        └── DependencyConfig.kt
```

## 주의사항

1. **과도한 추상화 주의**
   - 작은 프로젝트에서는 오버엔지니어링 가능
   - 팀 규모와 프로젝트 복잡도 고려

2. **레이어 간 데이터 전달**
   - 각 레이어별 DTO 사용
   - 도메인 모델이 외부에 노출되지 않도록

3. **의존성 방향 준수**
   - 안쪽 레이어가 바깥쪽에 의존하면 안 됨
   - 인터페이스를 통한 의존성 역전 활용

4. **Use Case 크기**
   - 하나의 Use Case는 하나의 기능
   - 너무 크면 분리 고려

## 결론

Clean Architecture는 의존성 규칙을 통해 시스템을 테스트 가능하고, 유지보수하기 쉽게 만듭니다. 도메인 로직을 프레임워크와 분리하여 기술 변화에 유연하게 대응할 수 있습니다. DDD의 전술적 패턴들(Entity, Value Object, Repository 등)과 자연스럽게 결합되며, 대규모 프로젝트에서 특히 효과적입니다.
