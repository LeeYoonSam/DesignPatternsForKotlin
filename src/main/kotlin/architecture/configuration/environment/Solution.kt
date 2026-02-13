package architecture.configuration.environment

import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Configuration Pattern - 해결책
 *
 * 12-Factor App 원칙에 따른 체계적인 설정 관리:
 * - 환경별 설정 분리: 개발/스테이징/프로덕션
 * - 타입 안전성: 컴파일 타임 타입 체크
 * - 설정 우선순위: 환경변수 > 프로필 > 기본값
 * - 민감 정보 분리: Secrets 별도 관리
 * - 검증: 시작 시점에 설정 검증
 * - 동적 리로드: 재시작 없이 설정 변경
 *
 * 핵심 구성:
 * - ConfigSource: 설정 소스 (환경변수, 파일, 시스템 프로퍼티)
 * - ConfigLoader: 설정 로더 및 병합
 * - TypedConfig: 타입 안전한 설정 접근
 * - ConfigValidator: 설정 검증
 * - ConfigWatcher: 동적 리로드
 */

// ============================================================
// 1. Environment (환경 정의)
// ============================================================

/**
 * 실행 환경
 */
enum class Environment(val profile: String) {
    DEVELOPMENT("dev"),
    STAGING("staging"),
    PRODUCTION("prod"),
    TEST("test");

    companion object {
        fun current(): Environment {
            val env = System.getenv("APP_ENV")
                ?: System.getProperty("app.env")
                ?: "dev"

            return entries.find { it.profile == env.lowercase() }
                ?: DEVELOPMENT
        }
    }
}

// ============================================================
// 2. Config Source (설정 소스)
// ============================================================

/**
 * 설정 소스 인터페이스
 */
interface ConfigSource {
    val name: String
    val priority: Int  // 높을수록 우선순위 높음
    fun load(): Map<String, String>
}

/**
 * 환경 변수 소스
 */
class EnvironmentVariableSource(
    private val prefix: String = "APP_"
) : ConfigSource {
    override val name = "EnvironmentVariables"
    override val priority = 300  // 가장 높은 우선순위

    override fun load(): Map<String, String> {
        return System.getenv()
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { (key, _) ->
                key.removePrefix(prefix)
                    .lowercase()
                    .replace("_", ".")
            }
    }
}

/**
 * 시스템 프로퍼티 소스
 */
class SystemPropertySource(
    private val prefix: String = "app."
) : ConfigSource {
    override val name = "SystemProperties"
    override val priority = 200

    override fun load(): Map<String, String> {
        return System.getProperties()
            .stringPropertyNames()
            .filter { it.startsWith(prefix) }
            .associateWith { System.getProperty(it) }
            .mapKeys { (key, _) -> key.removePrefix(prefix) }
    }
}

/**
 * Properties 파일 소스
 */
class PropertiesFileSource(
    private val filePath: String,
    override val priority: Int = 100
) : ConfigSource {
    override val name = "PropertiesFile($filePath)"

    override fun load(): Map<String, String> {
        val file = File(filePath)
        if (!file.exists()) {
            println("[Config] File not found: $filePath")
            return emptyMap()
        }

        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0].trim() to parts[1].trim()
                } else null
            }
            .toMap()
    }
}

/**
 * YAML 파일 소스 (간단한 파서)
 */
class YamlFileSource(
    private val filePath: String,
    override val priority: Int = 100
) : ConfigSource {
    override val name = "YamlFile($filePath)"

    override fun load(): Map<String, String> {
        val file = File(filePath)
        if (!file.exists()) {
            println("[Config] File not found: $filePath")
            return emptyMap()
        }

        val result = mutableMapOf<String, String>()
        val prefixStack = mutableListOf<String>()

        file.readLines().forEach { line ->
            if (line.isBlank() || line.trim().startsWith("#")) return@forEach

            val indent = line.takeWhile { it == ' ' }.length / 2
            val content = line.trim()

            // 스택 조정
            while (prefixStack.size > indent) {
                prefixStack.removeAt(prefixStack.lastIndex)
            }

            if (content.contains(": ")) {
                // key: value
                val (key, value) = content.split(": ", limit = 2)
                val fullKey = (prefixStack + key).joinToString(".")
                result[fullKey] = value.trim().removeSurrounding("\"").removeSurrounding("'")
            } else if (content.endsWith(":")) {
                // 중첩 키
                prefixStack.add(content.removeSuffix(":"))
            }
        }

        return result
    }
}

/**
 * 메모리 소스 (테스트용)
 */
class InMemorySource(
    private val data: Map<String, String>,
    override val priority: Int = 50
) : ConfigSource {
    override val name = "InMemory"
    override fun load(): Map<String, String> = data
}

// ============================================================
// 3. Config Loader (설정 로더)
// ============================================================

/**
 * 설정 로더 - 여러 소스 병합
 */
class ConfigLoader {
    private val sources = mutableListOf<ConfigSource>()
    private var mergedConfig = ConcurrentHashMap<String, String>()

    fun addSource(source: ConfigSource): ConfigLoader {
        sources.add(source)
        return this
    }

    fun load(): Map<String, String> {
        // 우선순위 낮은 것부터 로드 (높은 것이 덮어씀)
        val sorted = sources.sortedBy { it.priority }

        mergedConfig.clear()
        sorted.forEach { source ->
            val config = source.load()
            println("[Config] Loaded ${config.size} entries from ${source.name}")
            mergedConfig.putAll(config)
        }

        println("[Config] Total ${mergedConfig.size} configuration entries loaded")
        return mergedConfig.toMap()
    }

    fun get(key: String): String? = mergedConfig[key]

    fun getOrDefault(key: String, default: String): String = mergedConfig[key] ?: default

    fun reload() {
        load()
    }
}

// ============================================================
// 4. Typed Config (타입 안전한 설정)
// ============================================================

/**
 * 설정 값 래퍼
 */
sealed class ConfigValue<T> {
    abstract val key: String
    abstract val description: String
    abstract val required: Boolean
    abstract fun parse(value: String): T

    data class StringValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val default: String? = null
    ) : ConfigValue<String>() {
        override fun parse(value: String): String = value
    }

    data class IntValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val default: Int? = null,
        val min: Int? = null,
        val max: Int? = null
    ) : ConfigValue<Int>() {
        override fun parse(value: String): Int {
            val parsed = value.toIntOrNull()
                ?: throw ConfigParseException(key, value, "Int")

            if (min != null && parsed < min) {
                throw ConfigValidationException(key, "$parsed is less than minimum $min")
            }
            if (max != null && parsed > max) {
                throw ConfigValidationException(key, "$parsed is greater than maximum $max")
            }
            return parsed
        }
    }

    data class LongValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val default: Long? = null
    ) : ConfigValue<Long>() {
        override fun parse(value: String): Long =
            value.toLongOrNull() ?: throw ConfigParseException(key, value, "Long")
    }

    data class BooleanValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val default: Boolean? = null
    ) : ConfigValue<Boolean>() {
        override fun parse(value: String): Boolean = when (value.lowercase()) {
            "true", "yes", "1", "on", "enabled" -> true
            "false", "no", "0", "off", "disabled" -> false
            else -> throw ConfigParseException(key, value, "Boolean")
        }
    }

    data class DurationValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val default: Duration? = null
    ) : ConfigValue<Duration>() {
        // 예: "30s", "5m", "1h", "100ms"
        override fun parse(value: String): Duration {
            val regex = Regex("^(\\d+)(ms|s|m|h|d)$")
            val match = regex.matchEntire(value)
                ?: throw ConfigParseException(key, value, "Duration (e.g., 30s, 5m, 1h)")

            val (amount, unit) = match.destructured
            return when (unit) {
                "ms" -> Duration.ofMillis(amount.toLong())
                "s" -> Duration.ofSeconds(amount.toLong())
                "m" -> Duration.ofMinutes(amount.toLong())
                "h" -> Duration.ofHours(amount.toLong())
                "d" -> Duration.ofDays(amount.toLong())
                else -> throw ConfigParseException(key, value, "Duration")
            }
        }
    }

    data class ListValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val separator: String = ",",
        val default: List<String>? = null
    ) : ConfigValue<List<String>>() {
        override fun parse(value: String): List<String> =
            value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
    }

    data class UrlValue(
        override val key: String,
        override val description: String = "",
        override val required: Boolean = true,
        val default: String? = null
    ) : ConfigValue<String>() {
        override fun parse(value: String): String {
            if (!value.matches(Regex("^https?://.*"))) {
                throw ConfigValidationException(key, "Invalid URL format: $value")
            }
            return value
        }
    }
}

// 예외 클래스
class ConfigParseException(key: String, value: String, expectedType: String) :
    RuntimeException("Failed to parse config '$key': '$value' is not a valid $expectedType")

class ConfigValidationException(key: String, message: String) :
    RuntimeException("Configuration validation failed for '$key': $message")

class ConfigMissingException(key: String) :
    RuntimeException("Required configuration '$key' is missing")

// ============================================================
// 5. Config Property Delegate
// ============================================================

/**
 * 설정 프로퍼티 델리게이트
 */
class ConfigProperty<T>(
    private val config: TypedConfig,
    private val definition: ConfigValue<T>
) {
    private var cachedValue: T? = null

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (cachedValue == null) {
            cachedValue = config.get(definition)
        }
        return cachedValue as T
    }

    fun invalidate() {
        cachedValue = null
    }
}

// ============================================================
// 6. Typed Config (타입 안전한 설정 매니저)
// ============================================================

/**
 * 타입 안전한 설정 매니저
 */
class TypedConfig(private val loader: ConfigLoader) {
    private val changeListeners = CopyOnWriteArrayList<(String, String?, String?) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(definition: ConfigValue<T>): T {
        val rawValue = loader.get(definition.key)

        return when {
            rawValue != null -> definition.parse(rawValue)
            !definition.required -> getDefault(definition)
                ?: throw ConfigMissingException(definition.key)
            else -> getDefault(definition)
                ?: throw ConfigMissingException(definition.key)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getDefault(definition: ConfigValue<T>): T? = when (definition) {
        is ConfigValue.StringValue -> definition.default as T?
        is ConfigValue.IntValue -> definition.default as T?
        is ConfigValue.LongValue -> definition.default as T?
        is ConfigValue.BooleanValue -> definition.default as T?
        is ConfigValue.DurationValue -> definition.default as T?
        is ConfigValue.ListValue -> definition.default as T?
        is ConfigValue.UrlValue -> definition.default as T?
    }

    fun <T> property(definition: ConfigValue<T>): ConfigProperty<T> {
        return ConfigProperty(this, definition)
    }

    fun onChange(listener: (key: String, oldValue: String?, newValue: String?) -> Unit) {
        changeListeners.add(listener)
    }

    fun notifyChange(key: String, oldValue: String?, newValue: String?) {
        changeListeners.forEach { it(key, oldValue, newValue) }
    }
}

// ============================================================
// 7. Config Validator
// ============================================================

/**
 * 설정 검증기
 */
class ConfigValidator {
    private val rules = mutableListOf<ValidationRule>()

    fun addRule(rule: ValidationRule): ConfigValidator {
        rules.add(rule)
        return this
    }

    fun validate(config: Map<String, String>): ValidationResult {
        val errors = mutableListOf<String>()

        rules.forEach { rule ->
            val result = rule.validate(config)
            if (!result.isValid) {
                errors.addAll(result.errors)
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

interface ValidationRule {
    fun validate(config: Map<String, String>): ValidationResult
}

/**
 * 필수 키 검증
 */
class RequiredKeysRule(private val keys: List<String>) : ValidationRule {
    override fun validate(config: Map<String, String>): ValidationResult {
        val missing = keys.filter { it !in config }
        return if (missing.isEmpty()) {
            ValidationResult(true, emptyList())
        } else {
            ValidationResult(false, missing.map { "Required key '$it' is missing" })
        }
    }
}

/**
 * 포트 범위 검증
 */
class PortRangeRule(private val key: String) : ValidationRule {
    override fun validate(config: Map<String, String>): ValidationResult {
        val value = config[key] ?: return ValidationResult(true, emptyList())
        val port = value.toIntOrNull()

        return when {
            port == null -> ValidationResult(false, listOf("'$key' must be a number"))
            port < 0 || port > 65535 -> ValidationResult(false, listOf("'$key' must be between 0 and 65535"))
            else -> ValidationResult(true, emptyList())
        }
    }
}

/**
 * URL 형식 검증
 */
class UrlFormatRule(private val key: String) : ValidationRule {
    override fun validate(config: Map<String, String>): ValidationResult {
        val value = config[key] ?: return ValidationResult(true, emptyList())

        return if (value.matches(Regex("^https?://.*"))) {
            ValidationResult(true, emptyList())
        } else {
            ValidationResult(false, listOf("'$key' must be a valid URL"))
        }
    }
}

/**
 * 상호 의존 검증
 */
class DependencyRule(
    private val ifKey: String,
    private val ifValue: String,
    private val thenRequired: String
) : ValidationRule {
    override fun validate(config: Map<String, String>): ValidationResult {
        val actualValue = config[ifKey]

        return if (actualValue == ifValue && thenRequired !in config) {
            ValidationResult(
                false,
                listOf("When '$ifKey' is '$ifValue', '$thenRequired' is required")
            )
        } else {
            ValidationResult(true, emptyList())
        }
    }
}

// ============================================================
// 8. Config Watcher (동적 리로드)
// ============================================================

/**
 * 설정 파일 감시자
 */
class ConfigWatcher(
    private val filePath: String,
    private val onChange: () -> Unit
) {
    @Volatile
    private var running = false
    private var lastModified = 0L

    fun start() {
        running = true
        Thread {
            while (running) {
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        val currentModified = file.lastModified()
                        if (currentModified > lastModified) {
                            lastModified = currentModified
                            println("[ConfigWatcher] File changed: $filePath")
                            onChange()
                        }
                    }
                    Thread.sleep(1000)  // 1초마다 체크
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
    }
}

// ============================================================
// 9. Secrets Manager (민감 정보 관리)
// ============================================================

/**
 * 민감 정보 관리자
 */
interface SecretsManager {
    fun getSecret(key: String): String?
}

/**
 * 환경 변수 기반 Secrets
 */
class EnvironmentSecretsManager(
    private val prefix: String = "SECRET_"
) : SecretsManager {
    override fun getSecret(key: String): String? {
        return System.getenv("$prefix$key")
    }
}

/**
 * 파일 기반 Secrets (Docker secrets 스타일)
 */
class FileSecretsManager(
    private val secretsDir: String = "/run/secrets"
) : SecretsManager {
    override fun getSecret(key: String): String? {
        val file = File("$secretsDir/$key")
        return if (file.exists()) {
            file.readText().trim()
        } else null
    }
}

/**
 * 암호화된 Secrets (시뮬레이션)
 */
class EncryptedSecretsManager(
    private val encryptedFile: String,
    private val decryptionKey: String
) : SecretsManager {
    private val secrets = mutableMapOf<String, String>()

    init {
        // 실제로는 파일을 복호화하여 로드
        println("[Secrets] Loading encrypted secrets from $encryptedFile")
    }

    override fun getSecret(key: String): String? = secrets[key]
}

/**
 * Secret 참조를 해결하는 Config Source
 */
class SecretResolvingSource(
    private val delegate: ConfigSource,
    private val secretsManager: SecretsManager
) : ConfigSource {
    override val name = "SecretResolving(${delegate.name})"
    override val priority = delegate.priority

    override fun load(): Map<String, String> {
        return delegate.load().mapValues { (_, value) ->
            resolveSecrets(value)
        }
    }

    private fun resolveSecrets(value: String): String {
        // ${secret:KEY} 형식을 실제 값으로 치환
        val regex = Regex("\\$\\{secret:([^}]+)}")
        return regex.replace(value) { match ->
            val secretKey = match.groupValues[1]
            secretsManager.getSecret(secretKey)
                ?: throw RuntimeException("Secret not found: $secretKey")
        }
    }
}

// ============================================================
// 10. Application Config (실제 사용 예시)
// ============================================================

/**
 * 애플리케이션 설정 정의
 */
object AppConfigDefinitions {
    // 서버 설정
    val SERVER_HOST = ConfigValue.StringValue(
        key = "server.host",
        description = "Server bind host",
        default = "0.0.0.0"
    )

    val SERVER_PORT = ConfigValue.IntValue(
        key = "server.port",
        description = "Server listen port",
        default = 8080,
        min = 1,
        max = 65535
    )

    // 데이터베이스 설정
    val DB_URL = ConfigValue.UrlValue(
        key = "db.url",
        description = "Database connection URL"
    )

    val DB_POOL_SIZE = ConfigValue.IntValue(
        key = "db.pool.size",
        description = "Connection pool size",
        default = 10,
        min = 1,
        max = 100
    )

    val DB_TIMEOUT = ConfigValue.DurationValue(
        key = "db.timeout",
        description = "Connection timeout",
        default = Duration.ofSeconds(30)
    )

    // 캐시 설정
    val CACHE_ENABLED = ConfigValue.BooleanValue(
        key = "cache.enabled",
        description = "Enable caching",
        default = true
    )

    val CACHE_TTL = ConfigValue.DurationValue(
        key = "cache.ttl",
        description = "Cache TTL",
        default = Duration.ofMinutes(5)
    )

    // Feature flags
    val FEATURE_NEW_UI = ConfigValue.BooleanValue(
        key = "feature.new.ui",
        description = "Enable new UI",
        default = false
    )

    val FEATURE_DARK_MODE = ConfigValue.BooleanValue(
        key = "feature.dark.mode",
        description = "Enable dark mode",
        default = false
    )

    // 로깅
    val LOG_LEVEL = ConfigValue.StringValue(
        key = "log.level",
        description = "Log level",
        default = "INFO"
    )

    val LOG_FORMAT = ConfigValue.StringValue(
        key = "log.format",
        description = "Log format",
        default = "json"
    )

    // CORS
    val CORS_ORIGINS = ConfigValue.ListValue(
        key = "cors.origins",
        description = "Allowed CORS origins",
        default = listOf("http://localhost:3000")
    )
}

/**
 * 타입 안전한 설정 클래스
 */
class AppConfig(private val config: TypedConfig) {
    // 프로퍼티 델리게이트 사용
    val serverHost: String by config.property(AppConfigDefinitions.SERVER_HOST)
    val serverPort: Int by config.property(AppConfigDefinitions.SERVER_PORT)
    val dbUrl: String by config.property(AppConfigDefinitions.DB_URL)
    val dbPoolSize: Int by config.property(AppConfigDefinitions.DB_POOL_SIZE)
    val dbTimeout: Duration by config.property(AppConfigDefinitions.DB_TIMEOUT)
    val cacheEnabled: Boolean by config.property(AppConfigDefinitions.CACHE_ENABLED)
    val cacheTtl: Duration by config.property(AppConfigDefinitions.CACHE_TTL)
    val featureNewUi: Boolean by config.property(AppConfigDefinitions.FEATURE_NEW_UI)
    val featureDarkMode: Boolean by config.property(AppConfigDefinitions.FEATURE_DARK_MODE)
    val logLevel: String by config.property(AppConfigDefinitions.LOG_LEVEL)
    val corsOrigins: List<String> by config.property(AppConfigDefinitions.CORS_ORIGINS)
}

// ============================================================
// 11. Config Builder DSL
// ============================================================

/**
 * 설정 빌더 DSL
 */
class ConfigBuilder {
    private val loader = ConfigLoader()
    private val validator = ConfigValidator()
    private var secretsManager: SecretsManager? = null

    fun environment(prefix: String = "APP_") {
        loader.addSource(EnvironmentVariableSource(prefix))
    }

    fun systemProperties(prefix: String = "app.") {
        loader.addSource(SystemPropertySource(prefix))
    }

    fun propertiesFile(path: String, priority: Int = 100) {
        loader.addSource(PropertiesFileSource(path, priority))
    }

    fun yamlFile(path: String, priority: Int = 100) {
        loader.addSource(YamlFileSource(path, priority))
    }

    fun inMemory(data: Map<String, String>, priority: Int = 50) {
        loader.addSource(InMemorySource(data, priority))
    }

    fun secrets(manager: SecretsManager) {
        secretsManager = manager
    }

    fun validate(block: ConfigValidator.() -> Unit) {
        validator.apply(block)
    }

    fun build(): TypedConfig {
        // 설정 로드
        val rawConfig = loader.load()

        // 검증
        val result = validator.validate(rawConfig)
        if (!result.isValid) {
            throw RuntimeException(
                "Configuration validation failed:\n" +
                result.errors.joinToString("\n") { "  - $it" }
            )
        }

        return TypedConfig(loader)
    }
}

fun configureApp(block: ConfigBuilder.() -> Unit): TypedConfig {
    return ConfigBuilder().apply(block).build()
}

// ============================================================
// 12. 데모
// ============================================================

fun main() {
    println("=== Configuration Pattern - 환경별 설정 관리 ===\n")

    // --- 1. 현재 환경 확인 ---
    println("--- 1. 현재 환경 ---")
    val env = Environment.current()
    println("Current environment: $env (${env.profile})")

    // --- 2. 설정 로더 구성 ---
    println("\n--- 2. 설정 로더 구성 ---")

    // 테스트용 인메모리 설정
    val testConfig = mapOf(
        "server.host" to "localhost",
        "server.port" to "8080",
        "db.url" to "http://localhost:5432/testdb",
        "db.pool.size" to "5",
        "db.timeout" to "10s",
        "cache.enabled" to "true",
        "cache.ttl" to "5m",
        "feature.new.ui" to "true",
        "feature.dark.mode" to "false",
        "log.level" to "DEBUG",
        "cors.origins" to "http://localhost:3000,http://localhost:8080"
    )

    val config = configureApp {
        // 우선순위: 환경변수 > 시스템 프로퍼티 > 인메모리
        environment("APP_")
        systemProperties("app.")
        inMemory(testConfig)

        // 검증 규칙
        validate {
            addRule(PortRangeRule("server.port"))
            addRule(UrlFormatRule("db.url"))
        }
    }

    // --- 3. 타입 안전한 설정 접근 ---
    println("\n--- 3. 타입 안전한 설정 접근 ---")

    val appConfig = AppConfig(config)

    println("Server: ${appConfig.serverHost}:${appConfig.serverPort}")
    println("DB URL: ${appConfig.dbUrl}")
    println("DB Pool Size: ${appConfig.dbPoolSize}")
    println("DB Timeout: ${appConfig.dbTimeout.toMillis()}ms")
    println("Cache Enabled: ${appConfig.cacheEnabled}")
    println("Cache TTL: ${appConfig.cacheTtl.toMinutes()} minutes")
    println("Feature - New UI: ${appConfig.featureNewUi}")
    println("Feature - Dark Mode: ${appConfig.featureDarkMode}")
    println("Log Level: ${appConfig.logLevel}")
    println("CORS Origins: ${appConfig.corsOrigins}")

    // --- 4. 개별 설정 값 접근 ---
    println("\n--- 4. 개별 설정 값 접근 ---")

    val port: Int = config.get(AppConfigDefinitions.SERVER_PORT)
    val timeout: Duration = config.get(AppConfigDefinitions.DB_TIMEOUT)
    println("Port: $port, Timeout: $timeout")

    // --- 5. 설정 검증 ---
    println("\n--- 5. 설정 검증 ---")

    val validator = ConfigValidator()
        .addRule(RequiredKeysRule(listOf("server.port", "db.url")))
        .addRule(PortRangeRule("server.port"))
        .addRule(UrlFormatRule("db.url"))
        .addRule(DependencyRule("cache.enabled", "true", "cache.ttl"))

    val validConfig = mapOf(
        "server.port" to "8080",
        "db.url" to "http://localhost:5432/db",
        "cache.enabled" to "true",
        "cache.ttl" to "5m"
    )

    val validResult = validator.validate(validConfig)
    println("Valid config: ${validResult.isValid}")

    val invalidConfig = mapOf(
        "server.port" to "99999",  // 범위 초과
        "db.url" to "not-a-url"    // URL 형식 오류
    )

    val invalidResult = validator.validate(invalidConfig)
    println("Invalid config: ${invalidResult.isValid}")
    invalidResult.errors.forEach { println("  - $it") }

    // --- 6. Secrets 관리 ---
    println("\n--- 6. Secrets 관리 ---")

    val secretsManager = object : SecretsManager {
        private val secrets = mapOf(
            "DB_PASSWORD" to "super_secret_123",
            "API_KEY" to "sk_live_abc123"
        )
        override fun getSecret(key: String) = secrets[key]
    }

    // ${secret:KEY} 참조 해결
    val configWithSecrets = mapOf(
        "db.password" to "\${secret:DB_PASSWORD}",
        "api.key" to "\${secret:API_KEY}"
    )

    val resolvedConfig = SecretResolvingSource(
        InMemorySource(configWithSecrets),
        secretsManager
    ).load()

    println("Resolved secrets:")
    resolvedConfig.forEach { (k, v) ->
        val masked = if (v.length > 4) v.take(4) + "****" else "****"
        println("  $k = $masked")
    }

    // --- 7. 환경별 설정 ---
    println("\n--- 7. 환경별 설정 파일 구조 ---")
    println("""
        config/
        ├── application.yml           # 공통 설정
        ├── application-dev.yml       # 개발 환경
        ├── application-staging.yml   # 스테이징 환경
        ├── application-prod.yml      # 프로덕션 환경
        └── secrets/
            ├── db_password           # DB 비밀번호
            └── api_key               # API 키
    """.trimIndent())

    // --- 8. 동적 리로드 ---
    println("\n--- 8. 동적 리로드 (시뮬레이션) ---")

    config.onChange { key, old, new ->
        println("Config changed: $key = $old → $new")
    }

    println("ConfigWatcher를 사용하면 파일 변경 시 자동 리로드")

    // --- 핵심 원칙 ---
    println("\n=== Configuration Pattern 핵심 원칙 ===")
    println("1. 환경 분리: 코드와 설정 분리 (12-Factor App)")
    println("2. 우선순위: 환경변수 > 시스템 프로퍼티 > 파일 > 기본값")
    println("3. 타입 안전: 컴파일 타임 타입 체크")
    println("4. 검증: 시작 시점에 설정 검증")
    println("5. Secrets 분리: 민감 정보 별도 관리")
    println("6. 동적 리로드: 재시작 없이 설정 변경")
    println("7. 기본값: 필수/선택 구분, 합리적 기본값")
}
