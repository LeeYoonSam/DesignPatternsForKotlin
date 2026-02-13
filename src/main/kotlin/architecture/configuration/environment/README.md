# Configuration Pattern

## 개요

Configuration Pattern은 애플리케이션 설정을 체계적으로 관리하는 패턴입니다. **12-Factor App** 원칙에 따라 코드와 설정을 분리하고, **환경별 설정**, **타입 안전성**, **검증**, **민감 정보 분리**, **동적 리로드**를 제공합니다.

## 핵심 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| **ConfigSource** | 설정 소스 (환경변수, 파일, 시스템 프로퍼티) |
| **ConfigLoader** | 여러 소스를 우선순위대로 병합 |
| **TypedConfig** | 타입 안전한 설정 접근 |
| **ConfigValidator** | 시작 시점 설정 검증 |
| **ConfigWatcher** | 파일 변경 감지 및 동적 리로드 |
| **SecretsManager** | 민감 정보 별도 관리 |

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Configuration Sources                              │
│                                                                              │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│   │  Environment    │  │    System       │  │   Properties    │             │
│   │   Variables     │  │   Properties    │  │     Files       │             │
│   │                 │  │                 │  │                 │             │
│   │  APP_DB_HOST    │  │  -Dapp.db.host  │  │  db.host=...    │             │
│   │  APP_DB_PORT    │  │  -Dapp.db.port  │  │  db.port=...    │             │
│   │                 │  │                 │  │                 │             │
│   │  Priority: 300  │  │  Priority: 200  │  │  Priority: 100  │             │
│   └────────┬────────┘  └────────┬────────┘  └────────┬────────┘             │
│            │                    │                    │                       │
│            └────────────────────┼────────────────────┘                       │
│                                 │                                            │
│                                 ▼                                            │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                         ConfigLoader                                 │   │
│   │                                                                      │   │
│   │   1. 우선순위 낮은 것부터 로드                                        │   │
│   │   2. 높은 우선순위가 낮은 것을 덮어씀                                 │   │
│   │   3. 최종 병합된 설정 맵 생성                                         │   │
│   │                                                                      │   │
│   │   ┌───────────────────────────────────────────────────────────┐     │   │
│   │   │  mergedConfig = { db.host: "...", db.port: "...", ... }  │     │   │
│   │   └───────────────────────────────────────────────────────────┘     │   │
│   │                                                                      │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                        ConfigValidator                               │   │
│   │                                                                      │   │
│   │   • RequiredKeysRule: 필수 키 검증                                   │   │
│   │   • PortRangeRule: 포트 범위 검증 (0-65535)                          │   │
│   │   • UrlFormatRule: URL 형식 검증                                     │   │
│   │   • DependencyRule: 상호 의존 검증                                   │   │
│   │                                                                      │   │
│   │   → ValidationResult(isValid, errors)                                │   │
│   │                                                                      │   │
│   └──────────────────────────────┬──────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                          TypedConfig                                 │   │
│   │                                                                      │   │
│   │   ConfigValue.IntValue("db.port", min=1, max=65535, default=5432)   │   │
│   │                          │                                           │   │
│   │                          ▼                                           │   │
│   │   val port: Int = config.get(DB_PORT)  // 타입 안전!                 │   │
│   │                                                                      │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 설정 우선순위

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Priority (높을수록 우선)                            │
│                                                                              │
│   300  ┌─────────────────────────────────────────────────────────────┐      │
│        │  Environment Variables  (APP_DB_HOST=...)                   │      │
│        │  → 배포 환경에서 주입, 컨테이너 오케스트레이션               │      │
│        └─────────────────────────────────────────────────────────────┘      │
│                                    │                                         │
│   200  ┌─────────────────────────────────────────────────────────────┐      │
│        │  System Properties  (-Dapp.db.host=...)                     │      │
│        │  → JVM 시작 시 전달, 개발 환경 오버라이드                   │      │
│        └─────────────────────────────────────────────────────────────┘      │
│                                    │                                         │
│   100  ┌─────────────────────────────────────────────────────────────┐      │
│        │  Profile Config Files  (application-{env}.yml)              │      │
│        │  → 환경별 설정 파일                                         │      │
│        └─────────────────────────────────────────────────────────────┘      │
│                                    │                                         │
│    50  ┌─────────────────────────────────────────────────────────────┐      │
│        │  Default Config File  (application.yml)                     │      │
│        │  → 공통 기본 설정                                           │      │
│        └─────────────────────────────────────────────────────────────┘      │
│                                    │                                         │
│     0  ┌─────────────────────────────────────────────────────────────┐      │
│        │  Code Defaults  (default = 5432)                            │      │
│        │  → ConfigValue 정의의 기본값                                │      │
│        └─────────────────────────────────────────────────────────────┘      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 환경별 설정 파일 구조

```
config/
├── application.yml           # 공통 설정 (모든 환경)
├── application-dev.yml       # 개발 환경
├── application-staging.yml   # 스테이징 환경
├── application-prod.yml      # 프로덕션 환경
├── application-test.yml      # 테스트 환경
└── secrets/                  # 민감 정보 (Git 제외)
    ├── db_password
    ├── api_key
    └── jwt_secret
```

## 주요 구현

### ConfigSource 인터페이스

```kotlin
interface ConfigSource {
    val name: String
    val priority: Int  // 높을수록 우선
    fun load(): Map<String, String>
}

// 환경 변수 소스
class EnvironmentVariableSource(prefix: String = "APP_") : ConfigSource {
    override val priority = 300

    override fun load(): Map<String, String> {
        return System.getenv()
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix).lowercase().replace("_", ".") }
    }
}
```

### ConfigValue 정의

```kotlin
sealed class ConfigValue<T> {
    abstract val key: String
    abstract val required: Boolean
    abstract fun parse(value: String): T

    data class IntValue(
        override val key: String,
        val default: Int? = null,
        val min: Int? = null,
        val max: Int? = null
    ) : ConfigValue<Int>() {
        override fun parse(value: String): Int {
            val parsed = value.toIntOrNull()
                ?: throw ConfigParseException(key, value, "Int")
            // 범위 검증
            require(min == null || parsed >= min)
            require(max == null || parsed <= max)
            return parsed
        }
    }

    data class DurationValue(...) : ConfigValue<Duration>()
    data class BooleanValue(...) : ConfigValue<Boolean>()
    data class ListValue(...) : ConfigValue<List<String>>()
}
```

### 타입 안전한 설정 정의

```kotlin
object AppConfigDefinitions {
    val SERVER_PORT = ConfigValue.IntValue(
        key = "server.port",
        description = "Server listen port",
        default = 8080,
        min = 1,
        max = 65535
    )

    val DB_TIMEOUT = ConfigValue.DurationValue(
        key = "db.timeout",
        description = "Connection timeout",
        default = Duration.ofSeconds(30)
    )

    val FEATURE_NEW_UI = ConfigValue.BooleanValue(
        key = "feature.new.ui",
        default = false
    )
}

// 사용
val port: Int = config.get(AppConfigDefinitions.SERVER_PORT)
val timeout: Duration = config.get(AppConfigDefinitions.DB_TIMEOUT)
```

### Property Delegate

```kotlin
class AppConfig(private val config: TypedConfig) {
    val serverPort: Int by config.property(AppConfigDefinitions.SERVER_PORT)
    val dbTimeout: Duration by config.property(AppConfigDefinitions.DB_TIMEOUT)
    val featureNewUi: Boolean by config.property(AppConfigDefinitions.FEATURE_NEW_UI)
}

// 사용
val appConfig = AppConfig(config)
println("Port: ${appConfig.serverPort}")  // 타입 안전!
```

### Config Builder DSL

```kotlin
val config = configureApp {
    // 소스 등록 (우선순위 자동 적용)
    environment("APP_")           // 환경 변수
    systemProperties("app.")      // 시스템 프로퍼티
    yamlFile("config/application.yml")
    yamlFile("config/application-${env}.yml")

    // Secrets 관리자
    secrets(EnvironmentSecretsManager("SECRET_"))

    // 검증 규칙
    validate {
        addRule(RequiredKeysRule(listOf("db.url", "api.key")))
        addRule(PortRangeRule("server.port"))
        addRule(UrlFormatRule("db.url"))
    }
}
```

### ConfigValidator

```kotlin
class ConfigValidator {
    private val rules = mutableListOf<ValidationRule>()

    fun validate(config: Map<String, String>): ValidationResult {
        val errors = rules.flatMap { rule ->
            val result = rule.validate(config)
            if (!result.isValid) result.errors else emptyList()
        }
        return ValidationResult(errors.isEmpty(), errors)
    }
}

// 사용
val validator = ConfigValidator()
    .addRule(RequiredKeysRule(listOf("db.url")))
    .addRule(PortRangeRule("server.port"))
    .addRule(DependencyRule("cache.enabled", "true", "cache.ttl"))

val result = validator.validate(config)
if (!result.isValid) {
    throw RuntimeException("Config validation failed: ${result.errors}")
}
```

### SecretsManager

```kotlin
interface SecretsManager {
    fun getSecret(key: String): String?
}

// 환경 변수 기반
class EnvironmentSecretsManager(prefix: String = "SECRET_") : SecretsManager {
    override fun getSecret(key: String) = System.getenv("$prefix$key")
}

// Docker secrets 스타일
class FileSecretsManager(secretsDir: String = "/run/secrets") : SecretsManager {
    override fun getSecret(key: String): String? {
        val file = File("$secretsDir/$key")
        return if (file.exists()) file.readText().trim() else null
    }
}

// Secret 참조 해결: ${secret:DB_PASSWORD}
val resolvedValue = secretsManager.getSecret("DB_PASSWORD")
```

### ConfigWatcher (동적 리로드)

```kotlin
class ConfigWatcher(
    private val filePath: String,
    private val onChange: () -> Unit
) {
    fun start() {
        Thread {
            while (running) {
                if (file.lastModified() > lastModified) {
                    onChange()  // 설정 리로드
                }
                Thread.sleep(1000)
            }
        }.start()
    }
}

// 사용
val watcher = ConfigWatcher("config/application.yml") {
    config.reload()
    println("Configuration reloaded!")
}
watcher.start()
```

## 장점

1. **환경 분리**: 코드와 설정 완전 분리 (12-Factor App)
2. **타입 안전성**: 컴파일 타임 타입 체크, 오타 방지
3. **우선순위**: 환경변수 > 프로퍼티 > 파일 > 기본값
4. **검증**: 시작 시점에 설정 오류 감지
5. **Secrets 분리**: 민감 정보 코드/Git에서 분리
6. **동적 리로드**: 재시작 없이 설정 변경
7. **기본값**: 합리적 기본값으로 설정 누락 방지

## 단점

1. **복잡성**: 간단한 앱에는 과도할 수 있음
2. **런타임 오류**: 타입 변환 실패는 런타임에 발생
3. **디버깅**: 어떤 소스에서 값이 왔는지 추적 필요
4. **동기화**: 동적 리로드 시 스레드 안전성 고려

## 적용 시점

- 환경별로 다른 설정이 필요한 앱
- 컨테이너/클라우드 배포 환경
- 민감 정보(API 키, 비밀번호) 관리 필요
- Feature flag 동적 변경 필요
- 12-Factor App 원칙 준수

## 문제점 vs 해결책 비교

| 문제점 | 해결책 |
|--------|--------|
| 하드코딩된 설정 | ConfigSource로 외부화 |
| 환경 구분 없음 | Profile 기반 설정 파일 |
| 민감 정보 노출 | SecretsManager 분리 |
| 설정 분산 | 중앙화된 ConfigLoader |
| 타입 안전성 없음 | ConfigValue 타입 정의 |
| 기본값 없음 | ConfigValue.default |
| 검증 없음 | ConfigValidator |
| 재시작 필요 | ConfigWatcher 동적 리로드 |

## 관련 패턴

- **Strategy Pattern**: 환경별 다른 ConfigSource 전략
- **Builder Pattern**: Config DSL 빌더
- **Observer Pattern**: 설정 변경 알림
- **Singleton Pattern**: 전역 설정 인스턴스

## 실제 라이브러리

| 라이브러리 | 특징 |
|------------|------|
| **Spring Boot Config** | 자동 바인딩, Profile, Cloud Config |
| **Typesafe Config (HOCON)** | 타입 안전, 계층 구조 |
| **Dotenv** | .env 파일 기반 환경 변수 |
| **Vault** | HashiCorp Secrets 관리 |
| **AWS Secrets Manager** | 클라우드 Secrets 관리 |

## 12-Factor App 원칙

| 원칙 | 설명 | 이 패턴의 적용 |
|------|------|---------------|
| **III. Config** | 설정을 환경에 저장 | 환경변수 우선순위 |
| **X. Dev/prod parity** | 환경 간 차이 최소화 | Profile 기반 설정 |
| **XI. Logs** | 로그를 이벤트 스트림으로 | log.level 설정 |

## 참고 자료

- [The Twelve-Factor App](https://12factor.net/config)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Typesafe Config](https://github.com/lightbend/config)
- [HashiCorp Vault](https://www.vaultproject.io/)
