# Dependency Injection Container Pattern

## 개요

Dependency Injection Container Pattern은 애플리케이션의 의존성을 자동으로 생성, 관리, 주입하는 패턴입니다. Koin, Dagger, Spring과 같은 DI 프레임워크의 핵심 원리를 직접 구현하여 **모듈화**, **생명주기 관리**, **순환 의존성 감지**, **환경별 설정**을 제공합니다.

## 핵심 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| **Definition** | 의존성 생성 방법 정의 (타입, qualifier, lifecycle, factory) |
| **Module** | 관련 의존성들의 그룹 (모듈화) |
| **DIContainer** | 의존성 저장소 및 해결기 (핵심 컨테이너) |
| **Lifecycle** | 생명주기 관리 (Singleton, Factory, Scoped) |
| **Scope** | 특정 범위 내 인스턴스 공유 |
| **Qualifier** | 같은 타입의 다른 구현체 구분 |

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Application                                     │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                            DI.start { }                              │   │
│   │                                                                      │   │
│   │   modules(                                                           │   │
│   │       coreModule,                                                    │   │
│   │       databaseModule,                                                │   │
│   │       repositoryModule,                                              │   │
│   │       serviceModule                                                  │   │
│   │   )                                                                  │   │
│   │                                                                      │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                          DIContainer                                 │   │
│   │                                                                      │   │
│   │   ┌───────────────┐  ┌───────────────┐  ┌───────────────┐           │   │
│   │   │  definitions  │  │  singletons   │  │    scopes     │           │   │
│   │   │               │  │               │  │               │           │   │
│   │   │ Key→Definition│  │ Key→Instance  │  │ ScopeId→      │           │   │
│   │   │               │  │               │  │   Key→Instance│           │   │
│   │   └───────────────┘  └───────────────┘  └───────────────┘           │   │
│   │                                                                      │   │
│   │   ┌─────────────────────────────────────────────────────────────┐   │   │
│   │   │                    Resolution Engine                         │   │   │
│   │   │                                                              │   │   │
│   │   │   get<T>(qualifier) ──► Definition ──► create/cache ──► T   │   │   │
│   │   │                                                              │   │   │
│   │   │   Circular Dependency Detection: Stack-based tracking       │   │   │
│   │   │                                                              │   │   │
│   │   └─────────────────────────────────────────────────────────────┘   │   │
│   │                                                                      │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 의존성 해결 흐름

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Dependency Resolution Flow                           │
│                                                                              │
│   DI.get<UserService>()                                                      │
│         │                                                                    │
│         ▼                                                                    │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                      1. Find Definition                              │   │
│   │   Key = (UserService::class, null)                                   │   │
│   │   Definition = single { UserService(get(), get(), get()) }          │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                 2. Check Circular Dependency                         │   │
│   │   resolutionStack.add(Key)                                           │   │
│   │   if Key in stack → throw CircularDependencyException                │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                      3. Check Lifecycle                              │   │
│   │                                                                      │   │
│   │   SINGLETON ──────► singletons.getOrPut { create() }                │   │
│   │   FACTORY ────────► create() (always new)                           │   │
│   │   SCOPED ─────────► scopes[scopeId].getOrPut { create() }           │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                    4. Create Instance                                │   │
│   │                                                                      │   │
│   │   UserService(                                                       │   │
│   │       get<UserRepository>(),  ──► 재귀적 해결                        │   │
│   │       get<EmailService>(),    ──► 재귀적 해결                        │   │
│   │       get<Logger>()           ──► 재귀적 해결                        │   │
│   │   )                                                                  │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                      5. Return Instance                              │   │
│   │   resolutionStack.remove(Key)                                        │   │
│   │   return UserService instance                                        │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 생명주기 (Lifecycle)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Lifecycle Types                                 │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                          SINGLETON                                   │   │
│   │                                                                      │   │
│   │   get<A>() ──┐                                                       │   │
│   │              │──► [Instance A] ◄──┬── get<A>()                       │   │
│   │   get<A>() ──┘                    └── get<A>()                       │   │
│   │                                                                      │   │
│   │   • 앱 전체에서 하나의 인스턴스                                       │   │
│   │   • 상태 공유, 리소스 효율                                            │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                           FACTORY                                    │   │
│   │                                                                      │   │
│   │   get<A>() ──────► [Instance A1]                                     │   │
│   │   get<A>() ──────► [Instance A2]                                     │   │
│   │   get<A>() ──────► [Instance A3]                                     │   │
│   │                                                                      │   │
│   │   • 매번 새 인스턴스 생성                                             │   │
│   │   • 상태 격리, 일회성 객체                                            │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                           SCOPED                                     │   │
│   │                                                                      │   │
│   │   Scope "session-A":                                                 │   │
│   │   ├── get<A>() ──► [Instance A-scope1] ◄── get<A>()                  │   │
│   │   └── closeScope() → Instance A-scope1 destroyed                    │   │
│   │                                                                      │   │
│   │   Scope "session-B":                                                 │   │
│   │   ├── get<A>() ──► [Instance A-scope2] ◄── get<A>()                  │   │
│   │   └── closeScope() → Instance A-scope2 destroyed                    │   │
│   │                                                                      │   │
│   │   • 스코프 내에서 싱글톤                                              │   │
│   │   • 세션, 요청, Activity 생명주기                                     │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 주요 구현

### Module DSL

```kotlin
val coreModule = module("core") {
    // 싱글톤
    single<Logger> { ConsoleLogger() }

    // Qualifier로 구분
    single<Logger>(qualifier = "file") {
        FileLogger("/var/log/app.log")
    }

    // 팩토리 (매번 새 인스턴스)
    factory<HttpRequest> {
        HttpRequest(correlationId = UUID.randomUUID().toString())
    }

    // 스코프
    scoped<UserSession>("session") {
        UserSession(userId = getParameter("userId"))
    }
}
```

### DIContainer

```kotlin
class DIContainer {
    private val definitions = mutableMapOf<DefinitionKey, Definition<*>>()
    private val singletons = mutableMapOf<DefinitionKey, Any>()
    private val scopes = mutableMapOf<String, MutableMap<DefinitionKey, Any>>()

    fun loadModules(vararg modules: Module)

    fun <T : Any> get(type: KClass<T>, qualifier: String? = null): T

    fun createScope(scopeId: String): Scope

    fun closeScope(scopeId: String)

    fun close()
}
```

### 순환 의존성 감지

```kotlin
private fun <T : Any> resolve(key: DefinitionKey, definition: Definition<T>): T {
    val stack = resolutionStack.get() ?: mutableSetOf()

    // 순환 감지
    if (key in stack) {
        val chain = stack.joinToString(" → ") { it.type.simpleName }
        throw CircularDependencyException("Circular: $chain → ${key.type.simpleName}")
    }

    stack.add(key)
    try {
        return createInstance(definition)
    } finally {
        stack.remove(key)
    }
}
```

### 전역 DI (Koin 스타일)

```kotlin
object DI {
    private var container: DIContainer? = null

    fun start(block: DIBuilder.() -> Unit)
    inline fun <reified T : Any> get(qualifier: String? = null): T
    fun createScope(scopeId: String): Scope
    fun stop()
}

// 사용
DI.start {
    modules(coreModule, serviceModule)
}

val userService: UserService = DI.get()
val fileLogger: Logger = DI.get(qualifier = "file")
```

### Lazy Injection

```kotlin
// Property delegate
val userService: UserService by inject()

// 접근 시점에 주입됨
fun doSomething() {
    userService.registerUser(...)  // 여기서 실제 주입
}
```

### 환경별 모듈

```kotlin
// 개발 환경
val devModules = listOf(
    module("dev") {
        single<Database> { InMemoryDatabase() }
        single<EmailService> { MockEmailService() }
    }
)

// 프로덕션 환경
val prodModules = listOf(
    module("prod") {
        single<Database> {
            PostgresDatabase(
                host = getParameter("db.host"),
                port = getParameter("db.port")
            )
        }
        single<EmailService> {
            SmtpEmailService(getParameter("smtp.host"))
        }
    }
)

// 환경에 따라 선택
val env = System.getenv("ENV") ?: "dev"
DI.start {
    modules(
        *if (env == "prod") prodModules.toTypedArray()
        else devModules.toTypedArray()
    )
}
```

### 테스트 환경

```kotlin
class UserServiceTest {
    @Before
    fun setup() {
        DI.start {
            modules(
                module("test") {
                    single<Logger> { ConsoleLogger() }
                    single<Database> { InMemoryDatabase() }
                    single<EmailService> { MockEmailService() }
                    single<UserRepository> { UserRepositoryImpl(get(), get()) }
                    single { UserService(get(), get(), get()) }
                }
            )
        }
    }

    @Test
    fun `test user registration`() {
        val userService: UserService = DI.get()
        val mockEmail: MockEmailService = DI.get()

        val user = userService.registerUser("Test", "test@example.com")

        assertEquals("Test", user.name)
        assertEquals(1, mockEmail.sentEmails.size)
    }

    @After
    fun teardown() {
        DI.stop()
    }
}
```

## 장점

1. **자동 의존성 해결**: 깊은 의존성 체인 자동 생성
2. **생명주기 관리**: Singleton, Factory, Scoped 자동 관리
3. **순환 감지**: 순환 의존성 컴파일 시점 감지
4. **테스트 용이**: Mock 쉽게 주입
5. **환경 분리**: 개발/테스트/프로덕션 모듈 분리
6. **리소스 정리**: AutoCloseable 자동 close()
7. **모듈화**: 관련 의존성 그룹화

## 단점

1. **런타임 오버헤드**: 리플렉션 또는 맵 조회 비용
2. **디버깅 어려움**: 의존성 그래프 추적 필요
3. **러닝 커브**: DSL 및 개념 이해 필요
4. **컴파일 타임 체크 제한**: 런타임에 오류 발견 (Dagger는 컴파일 타임)

## 적용 시점

- 의존성 체인이 깊은 애플리케이션
- 환경별로 다른 구현체 필요
- 단위 테스트에서 Mock 주입 필요
- Activity/Fragment 등 생명주기별 스코프 관리

## 문제점 vs 해결책 비교

| 문제점 | 해결책 |
|--------|--------|
| 수동 의존성 생성 보일러플레이트 | Module DSL로 선언적 정의 |
| 하드코딩된 의존성 | 인터페이스 기반 주입 |
| 생명주기 관리 없음 | Singleton/Factory/Scoped |
| Mock 주입 어려움 | 테스트 모듈로 교체 |
| 순환 의존성 감지 불가 | Stack 기반 자동 감지 |
| 환경별 설정 if-else | 환경별 모듈 분리 |
| 의존성 변경 시 전파 | 컨테이너가 자동 해결 |

## 관련 패턴

- **Factory Pattern**: 객체 생성 캡슐화
- **Singleton Pattern**: 단일 인스턴스 보장
- **Service Locator**: 전역 레지스트리 (DI Container는 더 발전된 형태)
- **Strategy Pattern**: 구현체 교체 가능

## 실제 라이브러리

| 라이브러리 | 특징 |
|------------|------|
| **Koin** | Kotlin DSL 기반, 런타임 DI |
| **Dagger/Hilt** | 컴파일 타임 코드 생성, 성능 우수 |
| **Spring DI** | 어노테이션 기반, 풍부한 기능 |
| **Kodein** | Kotlin 멀티플랫폼 지원 |

## 참고 자료

- [Koin Documentation](https://insert-koin.io/)
- [Dagger Documentation](https://dagger.dev/)
- [Dependency Injection Principles](https://martinfowler.com/articles/injection.html)
- [Android DI Guide](https://developer.android.com/training/dependency-injection)
