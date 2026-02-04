package structural.plugin.editor

import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin Architecture Pattern - 해결책
 *
 * 텍스트 에디터를 플러그인 기반으로 설계하여
 * 기능을 동적으로 추가/제거하고, 제3자도 확장할 수 있도록 합니다.
 *
 * 핵심 구성:
 * - Plugin 인터페이스: 모든 플러그인이 구현하는 계약
 * - PluginManager: 플러그인 등록/해제/조회를 관리
 * - PluginHost (Editor): 플러그인에게 확장 포인트(Hook)를 제공
 * - Plugin Lifecycle: 초기화 → 활성화 → 비활성화 → 해제
 */

// ============================================================
// 1. 플러그인 인터페이스와 생명주기
// ============================================================

/**
 * 플러그인 메타데이터
 */
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val dependencies: List<String> = emptyList()
)

/**
 * 플러그인 생명주기 상태
 */
enum class PluginState {
    REGISTERED,    // 등록됨
    INITIALIZED,   // 초기화됨
    ACTIVE,        // 활성 상태
    INACTIVE,      // 비활성 상태
    ERROR          // 에러 발생
}

/**
 * 플러그인 기본 인터페이스
 * 모든 플러그인이 구현해야 하는 계약
 */
interface Plugin {
    val metadata: PluginMetadata

    /** 플러그인 초기화 - 리소스 할당, 설정 로드 */
    fun initialize(context: PluginContext)

    /** 플러그인 활성화 - Hook 등록, 기능 시작 */
    fun activate()

    /** 플러그인 비활성화 - Hook 해제, 기능 중단 */
    fun deactivate()

    /** 플러그인 해제 - 리소스 정리 */
    fun dispose()
}

/**
 * 플러그인에 제공되는 컨텍스트
 * 플러그인이 호스트의 기능에 접근하는 인터페이스
 */
interface PluginContext {
    /** 설정값 조회 */
    fun getConfig(key: String): String?

    /** 설정값 저장 */
    fun setConfig(key: String, value: String)

    /** 이벤트 발행 */
    fun emitEvent(event: EditorEvent)

    /** 이벤트 구독 */
    fun subscribe(eventType: String, handler: (EditorEvent) -> Unit)

    /** 다른 플러그인 조회 */
    fun getPlugin(pluginId: String): Plugin?

    /** 로그 기록 */
    fun log(pluginId: String, message: String)
}

// ============================================================
// 2. 확장 포인트 (Extension Points / Hooks)
// ============================================================

/**
 * 에디터 이벤트 시스템
 */
sealed class EditorEvent(val type: String) {
    data class ContentChanged(val content: String) : EditorEvent("content.changed")
    data class BeforeProcess(val content: String) : EditorEvent("content.beforeProcess")
    data class AfterProcess(val original: String, val processed: String) : EditorEvent("content.afterProcess")
    data class ThemeChanged(val themeName: String) : EditorEvent("theme.changed")
    data class PluginLoaded(val pluginId: String) : EditorEvent("plugin.loaded")
    data class PluginUnloaded(val pluginId: String) : EditorEvent("plugin.unloaded")
    data class CommandExecuted(val command: String, val args: Map<String, Any> = emptyMap()) : EditorEvent("command.executed")
    data class Custom(val customType: String, val data: Map<String, Any> = emptyMap()) : EditorEvent(customType)
}

/**
 * 콘텐츠 처리 플러그인 - 텍스트 변환 기능을 추가하는 확장 포인트
 */
interface ContentProcessorPlugin : Plugin {
    /** 처리 우선순위 (낮을수록 먼저 실행) */
    val priority: Int get() = 100

    /** 콘텐츠 처리 */
    fun process(content: String): String
}

/**
 * 테마 플러그인 - 에디터 테마를 확장하는 확장 포인트
 */
interface ThemePlugin : Plugin {
    data class ThemeColors(
        val background: String,
        val foreground: String,
        val accent: String,
        val selection: String,
        val lineNumber: String
    )

    val themeName: String
    fun getColors(): ThemeColors
}

/**
 * 커맨드 플러그인 - 에디터 명령을 추가하는 확장 포인트
 */
interface CommandPlugin : Plugin {
    data class Command(
        val id: String,
        val label: String,
        val shortcut: String? = null,
        val handler: (args: Map<String, Any>) -> CommandResult
    )

    data class CommandResult(
        val success: Boolean,
        val message: String = "",
        val data: Any? = null
    )

    fun getCommands(): List<Command>
}

/**
 * 툴바 플러그인 - 에디터 UI 확장 포인트
 */
interface ToolbarPlugin : Plugin {
    data class ToolbarItem(
        val id: String,
        val label: String,
        val icon: String,
        val tooltip: String,
        val onClick: () -> Unit
    )

    fun getToolbarItems(): List<ToolbarItem>
}

// ============================================================
// 3. 플러그인 매니저
// ============================================================

/**
 * 플러그인 매니저 - 플러그인 생명주기를 관리
 */
class PluginManager {
    private val plugins = mutableMapOf<String, PluginEntry>()
    private val eventHandlers = mutableMapOf<String, MutableList<(EditorEvent) -> Unit>>()
    private val configs = ConcurrentHashMap<String, String>()
    private val logs = mutableListOf<String>()

    data class PluginEntry(
        val plugin: Plugin,
        var state: PluginState = PluginState.REGISTERED
    )

    private val context = object : PluginContext {
        override fun getConfig(key: String): String? = configs[key]

        override fun setConfig(key: String, value: String) {
            configs[key] = value
        }

        override fun emitEvent(event: EditorEvent) {
            eventHandlers[event.type]?.forEach { handler ->
                try {
                    handler(event)
                } catch (e: Exception) {
                    log("system", "Event handler error: ${e.message}")
                }
            }
        }

        override fun subscribe(eventType: String, handler: (EditorEvent) -> Unit) {
            eventHandlers.getOrPut(eventType) { mutableListOf() }.add(handler)
        }

        override fun getPlugin(pluginId: String): Plugin? = plugins[pluginId]?.plugin

        override fun log(pluginId: String, message: String) {
            val logMessage = "[$pluginId] $message"
            logs.add(logMessage)
            println("  LOG: $logMessage")
        }
    }

    /** 플러그인 등록 */
    fun register(plugin: Plugin): PluginManager {
        val id = plugin.metadata.id

        if (plugins.containsKey(id)) {
            throw IllegalStateException("Plugin '$id' is already registered")
        }

        // 의존성 확인
        val missingDeps = plugin.metadata.dependencies.filter { !plugins.containsKey(it) }
        if (missingDeps.isNotEmpty()) {
            throw IllegalStateException(
                "Plugin '$id' has missing dependencies: ${missingDeps.joinToString()}"
            )
        }

        plugins[id] = PluginEntry(plugin, PluginState.REGISTERED)
        return this
    }

    /** 플러그인 초기화 및 활성화 */
    fun activate(pluginId: String) {
        val entry = plugins[pluginId]
            ?: throw IllegalArgumentException("Plugin '$pluginId' not found")

        if (entry.state == PluginState.ACTIVE) return

        try {
            // 초기화
            if (entry.state == PluginState.REGISTERED) {
                entry.plugin.initialize(context)
                entry.state = PluginState.INITIALIZED
            }

            // 활성화
            entry.plugin.activate()
            entry.state = PluginState.ACTIVE

            context.emitEvent(EditorEvent.PluginLoaded(pluginId))
            context.log("system", "Plugin '${entry.plugin.metadata.name}' activated")
        } catch (e: Exception) {
            entry.state = PluginState.ERROR
            context.log("system", "Failed to activate '$pluginId': ${e.message}")
            throw e
        }
    }

    /** 플러그인 비활성화 */
    fun deactivate(pluginId: String) {
        val entry = plugins[pluginId]
            ?: throw IllegalArgumentException("Plugin '$pluginId' not found")

        if (entry.state != PluginState.ACTIVE) return

        entry.plugin.deactivate()
        entry.state = PluginState.INACTIVE

        context.emitEvent(EditorEvent.PluginUnloaded(pluginId))
        context.log("system", "Plugin '${entry.plugin.metadata.name}' deactivated")
    }

    /** 플러그인 해제 (완전 제거) */
    fun unregister(pluginId: String) {
        val entry = plugins[pluginId] ?: return

        if (entry.state == PluginState.ACTIVE) {
            deactivate(pluginId)
        }

        // 이 플러그인에 의존하는 다른 플러그인 확인
        val dependents = plugins.values.filter {
            it.plugin.metadata.dependencies.contains(pluginId) && it.state == PluginState.ACTIVE
        }
        if (dependents.isNotEmpty()) {
            throw IllegalStateException(
                "Cannot unregister '$pluginId': " +
                    "plugins depend on it: ${dependents.map { it.plugin.metadata.id }}"
            )
        }

        entry.plugin.dispose()
        plugins.remove(pluginId)
        context.log("system", "Plugin '$pluginId' unregistered")
    }

    /** 모든 등록된 플러그인 활성화 (의존성 순서 고려) */
    fun activateAll() {
        val activated = mutableSetOf<String>()

        fun activateWithDeps(pluginId: String) {
            if (activated.contains(pluginId)) return
            val entry = plugins[pluginId] ?: return

            // 의존성 먼저 활성화
            entry.plugin.metadata.dependencies.forEach { depId ->
                activateWithDeps(depId)
            }

            activate(pluginId)
            activated.add(pluginId)
        }

        plugins.keys.forEach { activateWithDeps(it) }
    }

    /** 특정 타입의 활성 플러그인 조회 */
    inline fun <reified T : Plugin> getPlugins(): List<T> {
        return plugins.values
            .filter { it.state == PluginState.ACTIVE && it.plugin is T }
            .map { it.plugin as T }
    }

    /** 플러그인 상태 조회 */
    fun getPluginState(pluginId: String): PluginState? = plugins[pluginId]?.state

    /** 모든 플러그인 정보 조회 */
    fun listPlugins(): List<Pair<PluginMetadata, PluginState>> {
        return plugins.values.map { it.plugin.metadata to it.state }
    }

    fun getLogs(): List<String> = logs.toList()
}

// ============================================================
// 4. 플러그인 호스트 (에디터)
// ============================================================

/**
 * 플러그인 기반 에디터
 * 자체 기능은 최소화하고, 플러그인에게 확장 포인트를 제공
 */
class PluginEditor {
    val pluginManager = PluginManager()
    private var content: String = ""

    /** 콘텐츠 처리 - ContentProcessorPlugin 체인 실행 */
    fun processContent(text: String): String {
        val processors = pluginManager.getPlugins<ContentProcessorPlugin>()
            .sortedBy { it.priority }

        var result = text
        for (processor in processors) {
            result = processor.process(result)
        }

        return result
    }

    /** 사용 가능한 테마 목록 */
    fun getAvailableThemes(): List<String> {
        return pluginManager.getPlugins<ThemePlugin>().map { it.themeName }
    }

    /** 테마 적용 */
    fun applyTheme(themeName: String): ThemePlugin.ThemeColors? {
        return pluginManager.getPlugins<ThemePlugin>()
            .find { it.themeName == themeName }
            ?.getColors()
    }

    /** 사용 가능한 커맨드 목록 */
    fun getAvailableCommands(): List<CommandPlugin.Command> {
        return pluginManager.getPlugins<CommandPlugin>().flatMap { it.getCommands() }
    }

    /** 커맨드 실행 */
    fun executeCommand(commandId: String, args: Map<String, Any> = emptyMap()): CommandPlugin.CommandResult {
        val command = getAvailableCommands().find { it.id == commandId }
            ?: return CommandPlugin.CommandResult(false, "Command not found: $commandId")

        return command.handler(args)
    }

    /** 툴바 아이템 조회 */
    fun getToolbarItems(): List<ToolbarPlugin.ToolbarItem> {
        return pluginManager.getPlugins<ToolbarPlugin>().flatMap { it.getToolbarItems() }
    }
}

// ============================================================
// 5. 구체적인 플러그인 구현들
// ============================================================

/**
 * 마크다운 플러그인 - 마크다운 텍스트를 HTML로 변환
 */
class MarkdownPlugin : ContentProcessorPlugin {
    override val metadata = PluginMetadata(
        id = "markdown",
        name = "Markdown Processor",
        version = "1.0.0",
        author = "Editor Team",
        description = "마크다운 텍스트를 HTML로 변환합니다"
    )
    override val priority = 10  // 가장 먼저 실행

    private lateinit var context: PluginContext

    override fun initialize(context: PluginContext) {
        this.context = context
    }

    override fun activate() {
        context.log(metadata.id, "Markdown processor activated")
    }

    override fun deactivate() {}
    override fun dispose() {}

    override fun process(content: String): String {
        var result = content
        result = result.replace(Regex("^### (.+)", RegexOption.MULTILINE), "<h3>$1</h3>")
        result = result.replace(Regex("^## (.+)", RegexOption.MULTILINE), "<h2>$1</h2>")
        result = result.replace(Regex("^# (.+)", RegexOption.MULTILINE), "<h1>$1</h1>")
        result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        result = result.replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
        result = result.replace(Regex("^- (.+)", RegexOption.MULTILINE), "<li>$1</li>")
        return result
    }
}

/**
 * 코드 하이라이팅 플러그인
 */
class CodeHighlightPlugin : ContentProcessorPlugin {
    override val metadata = PluginMetadata(
        id = "code-highlight",
        name = "Code Highlighter",
        version = "1.0.0",
        author = "Editor Team",
        description = "코드 블록에 구문 강조를 적용합니다",
        dependencies = listOf("markdown")  // 마크다운 처리 후 실행
    )
    override val priority = 20

    private lateinit var context: PluginContext
    private val languageKeywords = mapOf(
        "kotlin" to listOf("fun", "val", "var", "class", "interface", "object", "when", "if", "else", "return"),
        "java" to listOf("public", "private", "class", "interface", "void", "int", "String", "return", "new"),
        "python" to listOf("def", "class", "import", "from", "return", "if", "else", "for", "in", "with")
    )

    override fun initialize(context: PluginContext) {
        this.context = context
    }

    override fun activate() {
        context.log(metadata.id, "Code highlighter activated with ${languageKeywords.size} languages")
    }

    override fun deactivate() {}
    override fun dispose() {}

    override fun process(content: String): String {
        return content.replace(Regex("```(\\w+)\\n([\\s\\S]*?)```")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2]
            val highlighted = highlightCode(language, code)
            "<pre class=\"language-$language\"><code>$highlighted</code></pre>"
        }
    }

    private fun highlightCode(language: String, code: String): String {
        val keywords = languageKeywords[language] ?: return code
        var result = code
        for (keyword in keywords) {
            result = result.replace(
                Regex("\\b($keyword)\\b"),
                "<span class=\"keyword\">$1</span>"
            )
        }
        return result
    }
}

/**
 * 이모지 플러그인
 */
class EmojiPlugin : ContentProcessorPlugin {
    override val metadata = PluginMetadata(
        id = "emoji",
        name = "Emoji Converter",
        version = "1.0.0",
        author = "Community",
        description = "텍스트 이모지 코드를 유니코드로 변환합니다"
    )
    override val priority = 50

    private lateinit var context: PluginContext
    private val emojiMap = mutableMapOf(
        ":smile:" to "\uD83D\uDE0A",
        ":heart:" to "\u2764\uFE0F",
        ":thumbsup:" to "\uD83D\uDC4D",
        ":fire:" to "\uD83D\uDD25",
        ":rocket:" to "\uD83D\uDE80",
        ":star:" to "\u2B50",
        ":check:" to "\u2705",
        ":warning:" to "\u26A0\uFE0F"
    )

    override fun initialize(context: PluginContext) {
        this.context = context
    }

    override fun activate() {
        context.log(metadata.id, "Emoji converter activated with ${emojiMap.size} emojis")
    }

    override fun deactivate() {}
    override fun dispose() {}

    override fun process(content: String): String {
        var result = content
        for ((code, emoji) in emojiMap) {
            result = result.replace(code, emoji)
        }
        return result
    }

    /** 커스텀 이모지 추가 */
    fun registerEmoji(code: String, emoji: String) {
        emojiMap[code] = emoji
    }
}

/**
 * 맞춤법 검사 플러그인
 */
class SpellCheckPlugin : ContentProcessorPlugin, CommandPlugin {
    override val metadata = PluginMetadata(
        id = "spell-check",
        name = "Spell Checker",
        version = "1.0.0",
        author = "Editor Team",
        description = "기본적인 맞춤법 검사와 자동 교정을 제공합니다"
    )
    override val priority = 90  // 다른 처리 이후 실행

    private lateinit var context: PluginContext
    private val corrections = mutableMapOf(
        "teh" to "the",
        "adn" to "and",
        "recieve" to "receive",
        "occured" to "occurred",
        "seperate" to "separate",
        "definately" to "definitely"
    )
    private var autoCorrectEnabled = true

    override fun initialize(context: PluginContext) {
        this.context = context
        autoCorrectEnabled = context.getConfig("spellcheck.autoCorrect")?.toBoolean() ?: true
    }

    override fun activate() {
        context.log(metadata.id, "Spell checker activated (autoCorrect=$autoCorrectEnabled)")
    }

    override fun deactivate() {}
    override fun dispose() {}

    override fun process(content: String): String {
        if (!autoCorrectEnabled) return content

        var result = content
        for ((wrong, correct) in corrections) {
            result = result.replace(Regex("\\b$wrong\\b", RegexOption.IGNORE_CASE), correct)
        }
        return result
    }

    override fun getCommands(): List<CommandPlugin.Command> = listOf(
        CommandPlugin.Command(
            id = "spellcheck.toggle",
            label = "Toggle Auto-Correct",
            shortcut = "Ctrl+Shift+S"
        ) {
            autoCorrectEnabled = !autoCorrectEnabled
            CommandPlugin.CommandResult(true, "Auto-correct: $autoCorrectEnabled")
        },
        CommandPlugin.Command(
            id = "spellcheck.addWord",
            label = "Add to Dictionary"
        ) { args ->
            val wrong = args["wrong"] as? String
            val correct = args["correct"] as? String
            if (wrong != null && correct != null) {
                corrections[wrong] = correct
                CommandPlugin.CommandResult(true, "Added correction: $wrong -> $correct")
            } else {
                CommandPlugin.CommandResult(false, "Missing 'wrong' or 'correct' argument")
            }
        }
    )
}

/**
 * 자동 링크 플러그인
 */
class AutoLinkPlugin : ContentProcessorPlugin {
    override val metadata = PluginMetadata(
        id = "auto-link",
        name = "Auto Link Detector",
        version = "1.0.0",
        author = "Community",
        description = "URL을 자동으로 클릭 가능한 링크로 변환합니다"
    )
    override val priority = 80

    private lateinit var context: PluginContext

    override fun initialize(context: PluginContext) {
        this.context = context
    }

    override fun activate() {
        context.log(metadata.id, "Auto link detector activated")
    }

    override fun deactivate() {}
    override fun dispose() {}

    override fun process(content: String): String {
        return content.replace(
            Regex("(?<!href=\")(https?://\\S+)"),
            "<a href=\"$1\">$1</a>"
        )
    }
}

/**
 * 다크 테마 플러그인
 */
class DarkThemePlugin : ThemePlugin {
    override val metadata = PluginMetadata(
        id = "theme-dark",
        name = "Dark Theme",
        version = "1.0.0",
        author = "Editor Team",
        description = "다크 테마를 제공합니다"
    )
    override val themeName = "Dark"

    override fun initialize(context: PluginContext) {}
    override fun activate() {}
    override fun deactivate() {}
    override fun dispose() {}

    override fun getColors() = ThemePlugin.ThemeColors(
        background = "#1E1E1E",
        foreground = "#D4D4D4",
        accent = "#569CD6",
        selection = "#264F78",
        lineNumber = "#858585"
    )
}

/**
 * Monokai 테마 플러그인
 */
class MonokaiThemePlugin : ThemePlugin {
    override val metadata = PluginMetadata(
        id = "theme-monokai",
        name = "Monokai Theme",
        version = "1.0.0",
        author = "Community",
        description = "Monokai 컬러 테마를 제공합니다"
    )
    override val themeName = "Monokai"

    override fun initialize(context: PluginContext) {}
    override fun activate() {}
    override fun deactivate() {}
    override fun dispose() {}

    override fun getColors() = ThemePlugin.ThemeColors(
        background = "#272822",
        foreground = "#F8F8F2",
        accent = "#A6E22E",
        selection = "#49483E",
        lineNumber = "#90908A"
    )
}

/**
 * 단어 수 플러그인 - 커맨드와 툴바를 제공하는 복합 플러그인
 */
class WordCountPlugin : CommandPlugin, ToolbarPlugin {
    override val metadata = PluginMetadata(
        id = "word-count",
        name = "Word Counter",
        version = "1.0.0",
        author = "Editor Team",
        description = "단어 수, 문자 수, 줄 수를 계산합니다"
    )

    private lateinit var context: PluginContext
    private var lastContent = ""

    override fun initialize(context: PluginContext) {
        this.context = context
    }

    override fun activate() {
        context.subscribe("content.changed") { event ->
            if (event is EditorEvent.ContentChanged) {
                lastContent = event.content
            }
        }
        context.log(metadata.id, "Word counter activated")
    }

    override fun deactivate() {}
    override fun dispose() {}

    private fun countWords(text: String): Int =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    private fun countLines(text: String): Int =
        text.lines().size

    override fun getCommands(): List<CommandPlugin.Command> = listOf(
        CommandPlugin.Command(
            id = "wordcount.count",
            label = "Show Word Count",
            shortcut = "Ctrl+Shift+W"
        ) { args ->
            val text = args["text"] as? String ?: lastContent
            val words = countWords(text)
            val chars = text.length
            val lines = countLines(text)
            CommandPlugin.CommandResult(
                true,
                "Words: $words, Characters: $chars, Lines: $lines",
                mapOf("words" to words, "characters" to chars, "lines" to lines)
            )
        }
    )

    override fun getToolbarItems(): List<ToolbarPlugin.ToolbarItem> = listOf(
        ToolbarPlugin.ToolbarItem(
            id = "wordcount.toolbar",
            label = "Word Count",
            icon = "counter",
            tooltip = "Show document statistics"
        ) {
            val words = countWords(lastContent)
            println("  [Toolbar] Words: $words")
        }
    )
}

// ============================================================
// 6. 플러그인 DSL 빌더
// ============================================================

/**
 * 간단한 플러그인을 DSL로 생성
 */
class SimpleContentPlugin(
    override val metadata: PluginMetadata,
    override val priority: Int,
    private val processor: (String) -> String
) : ContentProcessorPlugin {
    private lateinit var context: PluginContext

    override fun initialize(context: PluginContext) {
        this.context = context
    }

    override fun activate() {
        context.log(metadata.id, "${metadata.name} activated")
    }

    override fun deactivate() {}
    override fun dispose() {}

    override fun process(content: String): String = processor(content)
}

class PluginBuilder {
    var id: String = ""
    var name: String = ""
    var version: String = "1.0.0"
    var author: String = "Anonymous"
    var description: String = ""
    var priority: Int = 100
    var dependencies: List<String> = emptyList()
    private var processor: (String) -> String = { it }

    fun process(block: (String) -> String) {
        processor = block
    }

    fun build(): ContentProcessorPlugin = SimpleContentPlugin(
        metadata = PluginMetadata(id, name, version, author, description, dependencies),
        priority = priority,
        processor = processor
    )
}

fun contentPlugin(block: PluginBuilder.() -> Unit): ContentProcessorPlugin {
    return PluginBuilder().apply(block).build()
}

// ============================================================
// 7. 플러그인 레지스트리 (ServiceLoader 시뮬레이션)
// ============================================================

/**
 * 플러그인 레지스트리 - 외부 플러그인 검색/로드
 * 실제로는 ServiceLoader나 classpath 스캔을 사용하지만
 * 여기서는 수동 등록으로 시뮬레이션
 */
object PluginRegistry {
    private val availablePlugins = mutableMapOf<String, () -> Plugin>()

    fun register(pluginId: String, factory: () -> Plugin) {
        availablePlugins[pluginId] = factory
    }

    fun discover(): List<PluginMetadata> {
        return availablePlugins.map { (_, factory) -> factory().metadata }
    }

    fun create(pluginId: String): Plugin? {
        return availablePlugins[pluginId]?.invoke()
    }

    fun createAll(): List<Plugin> {
        return availablePlugins.values.map { it() }
    }

    /** 초기화 - 기본 플러그인 등록 */
    fun init() {
        register("markdown") { MarkdownPlugin() }
        register("code-highlight") { CodeHighlightPlugin() }
        register("emoji") { EmojiPlugin() }
        register("spell-check") { SpellCheckPlugin() }
        register("auto-link") { AutoLinkPlugin() }
        register("theme-dark") { DarkThemePlugin() }
        register("theme-monokai") { MonokaiThemePlugin() }
        register("word-count") { WordCountPlugin() }
    }
}

// ============================================================
// 데모
// ============================================================

fun main() {
    println("=== Plugin Architecture Pattern - 텍스트 에디터 ===\n")

    // --- 1. 에디터와 플러그인 매니저 설정 ---
    println("--- 1. 플러그인 등록 및 활성화 ---")
    val editor = PluginEditor()

    // 핵심 플러그인 등록
    editor.pluginManager
        .register(MarkdownPlugin())
        .register(CodeHighlightPlugin())    // markdown에 의존
        .register(EmojiPlugin())
        .register(SpellCheckPlugin())
        .register(AutoLinkPlugin())
        .register(DarkThemePlugin())
        .register(MonokaiThemePlugin())
        .register(WordCountPlugin())

    // 모든 플러그인 활성화 (의존성 순서 자동 처리)
    editor.pluginManager.activateAll()

    println("\n등록된 플러그인:")
    editor.pluginManager.listPlugins().forEach { (meta, state) ->
        println("  [${state.name.padEnd(11)}] ${meta.name} v${meta.version} - ${meta.description}")
    }

    // --- 2. 콘텐츠 처리 ---
    println("\n--- 2. 콘텐츠 처리 (플러그인 체인) ---")
    val rawContent = """
# Hello World
This is **bold** and *italic* text.
- Item one
- Item two
Teh quick brown fox adn teh lazy dog.
Check this: https://kotlinlang.org
I :heart: Kotlin :fire:
```kotlin
fun main() {
    val greeting = "Hello"
    println(greeting)
}
```
    """.trimIndent()

    println("원본:")
    println(rawContent)
    println()

    val processed = editor.processContent(rawContent)
    println("처리 결과:")
    println(processed)

    // --- 3. 테마 시스템 ---
    println("\n--- 3. 테마 플러그인 ---")
    println("사용 가능한 테마: ${editor.getAvailableThemes()}")

    val darkColors = editor.applyTheme("Dark")
    println("Dark 테마 적용: $darkColors")

    val monokaiColors = editor.applyTheme("Monokai")
    println("Monokai 테마 적용: $monokaiColors")

    // --- 4. 커맨드 시스템 ---
    println("\n--- 4. 커맨드 플러그인 ---")
    println("사용 가능한 커맨드:")
    editor.getAvailableCommands().forEach { cmd ->
        println("  ${cmd.id} (${cmd.shortcut ?: "no shortcut"}) - ${cmd.label}")
    }

    // 단어 수 커맨드 실행
    val wordCountResult = editor.executeCommand("wordcount.count", mapOf("text" to rawContent))
    println("\n단어 수 결과: ${wordCountResult.message}")

    // 맞춤법 검사 토글
    val toggleResult = editor.executeCommand("spellcheck.toggle")
    println("맞춤법 토글: ${toggleResult.message}")

    // 단어 추가
    val addWordResult = editor.executeCommand(
        "spellcheck.addWord",
        mapOf("wrong" to "kotlin", "correct" to "Kotlin")
    )
    println("단어 추가: ${addWordResult.message}")

    // --- 5. 툴바 시스템 ---
    println("\n--- 5. 툴바 플러그인 ---")
    println("툴바 아이템:")
    editor.getToolbarItems().forEach { item ->
        println("  [${item.icon}] ${item.label} - ${item.tooltip}")
    }

    // --- 6. DSL로 커스텀 플러그인 생성 ---
    println("\n--- 6. DSL로 커스텀 플러그인 생성 ---")

    val tableOfContents = contentPlugin {
        id = "toc-generator"
        name = "Table of Contents"
        version = "1.0.0"
        author = "Community"
        description = "제목 태그에서 목차를 자동 생성합니다"
        priority = 200  // 마크다운 처리 후 실행

        process { content ->
            val headings = Regex("<h([1-3])>(.*?)</h[1-3]>")
                .findAll(content)
                .map { match ->
                    val level = match.groupValues[1].toInt()
                    val text = match.groupValues[2]
                    "${"  ".repeat(level - 1)}- $text"
                }
                .toList()

            if (headings.isNotEmpty()) {
                val toc = headings.joinToString("\n", prefix = "[TOC]\n", postfix = "\n[/TOC]\n\n")
                toc + content
            } else {
                content
            }
        }
    }

    editor.pluginManager.register(tableOfContents)
    editor.pluginManager.activate("toc-generator")

    val contentWithToc = editor.processContent("# Title\n## Section 1\n### Subsection\n## Section 2\nSome text")
    println("목차 포함 결과:")
    println(contentWithToc)

    // --- 7. 플러그인 동적 제거 ---
    println("\n--- 7. 플러그인 동적 관리 ---")
    println("이모지 플러그인 비활성화 전:")
    println("  ${editor.processContent(":heart: :fire: :rocket:")}")

    editor.pluginManager.deactivate("emoji")
    println("이모지 플러그인 비활성화 후:")
    println("  ${editor.processContent(":heart: :fire: :rocket:")}")

    // 재활성화
    editor.pluginManager.activate("emoji")
    println("이모지 플러그인 재활성화 후:")
    println("  ${editor.processContent(":heart: :fire: :rocket:")}")

    // --- 8. 플러그인 레지스트리 ---
    println("\n--- 8. 플러그인 레지스트리 (ServiceLoader 시뮬레이션) ---")
    PluginRegistry.init()

    println("레지스트리에서 발견된 플러그인:")
    PluginRegistry.discover().forEach { meta ->
        println("  ${meta.id}: ${meta.name} v${meta.version} by ${meta.author}")
    }

    // --- 9. 최종 상태 ---
    println("\n--- 9. 최종 플러그인 상태 ---")
    editor.pluginManager.listPlugins().forEach { (meta, state) ->
        val deps = if (meta.dependencies.isNotEmpty()) " (depends: ${meta.dependencies})" else ""
        println("  [${state.name.padEnd(11)}] ${meta.id}$deps")
    }

    println("\n=== Plugin Architecture 핵심 이점 ===")
    println("1. OCP 준수: 에디터 코드 수정 없이 기능 확장")
    println("2. SRP 준수: 각 플러그인이 하나의 기능만 담당")
    println("3. 동적 관리: 런타임에 플러그인 활성화/비활성화")
    println("4. 의존성 관리: 플러그인 간 의존성 순서 자동 처리")
    println("5. 격리된 테스트: 각 플러그인을 독립적으로 테스트 가능")
    println("6. 제3자 확장: 인터페이스만 구현하면 누구나 플러그인 제작 가능")
}
