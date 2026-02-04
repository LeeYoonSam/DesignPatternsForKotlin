# Plugin Architecture Pattern

## 개요

Plugin Architecture(플러그인 아키텍처)는 애플리케이션의 **핵심 기능을 최소화**하고, 추가 기능을 **독립적인 플러그인 모듈**로 분리하여 동적으로 추가/제거할 수 있게 하는 패턴입니다. 에디터 수정 없이 새 기능을 확장할 수 있어 **개방-폐쇄 원칙(OCP)**을 완벽하게 준수합니다.

## 핵심 개념

### 구성 요소

| 구성 요소 | 설명 | 예시 |
|-----------|------|------|
| **Plugin Interface** | 모든 플러그인이 구현하는 계약 | `Plugin`, `ContentProcessorPlugin` |
| **Plugin Host** | 플러그인에 확장 포인트를 제공하는 호스트 앱 | `PluginEditor` |
| **Plugin Manager** | 플러그인 생명주기 관리 | 등록, 활성화, 비활성화, 해제 |
| **Extension Point** | 호스트가 제공하는 확장 가능 지점 | 콘텐츠 처리, 테마, 커맨드, 툴바 |
| **Plugin Context** | 플러그인이 호스트에 접근하는 인터페이스 | 설정, 이벤트, 로그 |
| **Plugin Registry** | 사용 가능한 플러그인을 검색/로드 | `ServiceLoader`, classpath 스캔 |

### 아키텍처 다이어그램

```
┌──────────────────────────────────────────────────────────┐
│                    Plugin Host (Editor)                    │
│  ┌─────────────────────────────────────────────────────┐  │
│  │              Plugin Manager                          │  │
│  │  - register(plugin)     - activate(id)              │  │
│  │  - deactivate(id)       - unregister(id)            │  │
│  │  - getPlugins<T>()      - activateAll()             │  │
│  └───────────────────┬─────────────────────────────────┘  │
│                      │                                     │
│  ┌───────────────────┴─────────────────────────────────┐  │
│  │              Extension Points (Hooks)                │  │
│  │  - ContentProcessorPlugin (텍스트 변환)              │  │
│  │  - ThemePlugin            (테마 확장)                │  │
│  │  - CommandPlugin          (명령 추가)                │  │
│  │  - ToolbarPlugin          (UI 확장)                  │  │
│  └───────────────────┬─────────────────────────────────┘  │
│                      │                                     │
│  ┌───────────────────┴─────────────────────────────────┐  │
│  │              Plugin Context                          │  │
│  │  - getConfig/setConfig   - emitEvent/subscribe      │  │
│  │  - getPlugin(id)         - log(message)             │  │
│  └─────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
         │              │              │              │
    ┌────┴────┐   ┌────┴────┐   ┌────┴────┐   ┌────┴────┐
    │Markdown │   │  Code   │   │  Emoji  │   │  Spell  │
    │ Plugin  │   │Highlight│   │ Plugin  │   │  Check  │
    │ (p:10)  │   │ (p:20)  │   │ (p:50)  │   │ (p:90)  │
    └─────────┘   └─────────┘   └─────────┘   └─────────┘
    ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
    │  Dark   │   │ Monokai │   │  Word   │   │  Auto   │
    │  Theme  │   │  Theme  │   │  Count  │   │  Link   │
    └─────────┘   └─────────┘   └─────────┘   └─────────┘
```

### 플러그인 생명주기

```
REGISTERED → INITIALIZED → ACTIVE ⇄ INACTIVE
                             ↓
                           ERROR
                             ↓ (RetryFromError)
                         INITIALIZED

register() → initialize(context) → activate() → deactivate()
                                                      ↓
                                                  dispose()
```

## 구현 상세

### 1. 플러그인 인터페이스

```kotlin
interface Plugin {
    val metadata: PluginMetadata

    fun initialize(context: PluginContext)  // 리소스 할당, 설정 로드
    fun activate()                          // Hook 등록, 기능 시작
    fun deactivate()                        // Hook 해제, 기능 중단
    fun dispose()                           // 리소스 정리
}

data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val dependencies: List<String> = emptyList()
)
```

### 2. 확장 포인트 (Extension Points)

```kotlin
// 콘텐츠 처리 확장 포인트
interface ContentProcessorPlugin : Plugin {
    val priority: Int get() = 100  // 실행 순서 (낮을수록 먼저)
    fun process(content: String): String
}

// 테마 확장 포인트
interface ThemePlugin : Plugin {
    val themeName: String
    fun getColors(): ThemeColors
}

// 커맨드 확장 포인트
interface CommandPlugin : Plugin {
    fun getCommands(): List<Command>
}

// 툴바 확장 포인트
interface ToolbarPlugin : Plugin {
    fun getToolbarItems(): List<ToolbarItem>
}
```

### 3. 플러그인 컨텍스트

```kotlin
interface PluginContext {
    fun getConfig(key: String): String?
    fun setConfig(key: String, value: String)
    fun emitEvent(event: EditorEvent)
    fun subscribe(eventType: String, handler: (EditorEvent) -> Unit)
    fun getPlugin(pluginId: String): Plugin?
    fun log(pluginId: String, message: String)
}
```

### 4. 플러그인 매니저 (의존성 순서 활성화)

```kotlin
class PluginManager {
    fun register(plugin: Plugin): PluginManager { ... }
    fun activate(pluginId: String) { ... }
    fun deactivate(pluginId: String) { ... }
    fun unregister(pluginId: String) { ... }

    // 의존성 그래프 기반 자동 순서 활성화
    fun activateAll() {
        fun activateWithDeps(pluginId: String) {
            if (activated.contains(pluginId)) return
            val entry = plugins[pluginId] ?: return
            entry.plugin.metadata.dependencies.forEach { activateWithDeps(it) }
            activate(pluginId)
            activated.add(pluginId)
        }
        plugins.keys.forEach { activateWithDeps(it) }
    }

    // 타입 안전한 플러그인 조회
    inline fun <reified T : Plugin> getPlugins(): List<T>
}
```

### 5. 플러그인 호스트 (에디터)

```kotlin
class PluginEditor {
    val pluginManager = PluginManager()

    // 콘텐츠 처리 - 우선순위 기반 체인 실행
    fun processContent(text: String): String {
        val processors = pluginManager.getPlugins<ContentProcessorPlugin>()
            .sortedBy { it.priority }  // 10 → 20 → 50 → 90

        var result = text
        for (processor in processors) {
            result = processor.process(result)
        }
        return result
    }
}
```

### 6. DSL 기반 플러그인 생성

```kotlin
val tocPlugin = contentPlugin {
    id = "toc-generator"
    name = "Table of Contents"
    priority = 200

    process { content ->
        val headings = Regex("<h([1-3])>(.*?)</h[1-3]>")
            .findAll(content)
            .map { /* 목차 항목 생성 */ }
            .toList()
        if (headings.isNotEmpty()) toc + content else content
    }
}
```

## 처리 파이프라인

```
원본 텍스트
    │
    ▼ priority: 10
┌───────────────┐
│   Markdown    │  "**bold**" → "<b>bold</b>"
│   Plugin      │  "# Title" → "<h1>Title</h1>"
└───────┬───────┘
        ▼ priority: 20
┌───────────────┐
│    Code       │  "```kotlin...```" → "<pre><code>...</code></pre>"
│  Highlight    │  keywords → <span class="keyword">
└───────┬───────┘
        ▼ priority: 50
┌───────────────┐
│    Emoji      │  ":heart:" → "❤️"
│   Plugin      │  ":fire:" → "🔥"
└───────┬───────┘
        ▼ priority: 80
┌───────────────┐
│  Auto Link    │  "https://..." → "<a href=...>...</a>"
│   Plugin      │
└───────┬───────┘
        ▼ priority: 90
┌───────────────┐
│    Spell      │  "teh" → "the"
│    Check      │  "adn" → "and"
└───────┬───────┘
        ▼
최종 HTML 결과
```

## 복합 플러그인 (다중 확장 포인트 구현)

```kotlin
// 하나의 플러그인이 여러 확장 포인트를 동시에 구현
class SpellCheckPlugin : ContentProcessorPlugin, CommandPlugin {
    // ContentProcessorPlugin: 텍스트 자동 교정
    override fun process(content: String): String { ... }

    // CommandPlugin: 토글, 단어 추가 명령
    override fun getCommands(): List<Command> = listOf(
        Command("spellcheck.toggle", "Toggle Auto-Correct") { ... },
        Command("spellcheck.addWord", "Add to Dictionary") { ... }
    )
}

class WordCountPlugin : CommandPlugin, ToolbarPlugin {
    // CommandPlugin: 단어 수 명령
    // ToolbarPlugin: 툴바에 단어 수 표시
}
```

## 장점

1. **OCP 준수**: 에디터 코드를 수정하지 않고 기능을 확장
2. **SRP 준수**: 각 플러그인이 단일 기능에 집중
3. **동적 관리**: 런타임에 플러그인 활성화/비활성화
4. **의존성 관리**: 플러그인 간 의존성 순서 자동 처리
5. **격리된 테스트**: 각 플러그인을 독립적으로 테스트 가능
6. **제3자 확장**: 인터페이스만 구현하면 누구나 플러그인 제작 가능
7. **우선순위 제어**: 처리 순서를 priority로 명시적 관리

## 단점

1. **복잡성 증가**: Plugin, Context, Manager 등 추가 추상화 필요
2. **플러그인 간 통신**: 직접 호출 대신 이벤트 시스템 경유 (간접성)
3. **버전 호환성**: 호스트 API 변경 시 플러그인 호환성 관리 필요
4. **디버깅 어려움**: 여러 플러그인이 체인으로 처리하면 문제 추적이 복잡
5. **성능 오버헤드**: 플러그인 로딩/관리에 따른 추가 비용

## 적용 시점

- IDE/에디터 (VS Code, IntelliJ, Vim)
- 브라우저 확장 프로그램 (Chrome Extensions)
- 빌드 시스템 (Gradle, webpack)
- CMS 시스템 (WordPress)
- 미디어 플레이어 (코덱 플러그인)
- CI/CD 도구 (Jenkins, GitHub Actions)
- 게임 엔진 (모드 시스템)

## 실제 사례

| 프로젝트 | 플러그인 메커니즘 |
|----------|-------------------|
| IntelliJ IDEA | Extension Point + Service |
| VS Code | Extension API + Contribution Points |
| Gradle | Plugin interface + apply() |
| Chrome | chrome.* API + manifest.json |
| WordPress | Hook (action/filter) + Plugin API |
| webpack | Tapable hooks + Plugin class |

## 관련 패턴

- **Strategy Pattern**: 플러그인이 특정 전략을 캡슐화하는 것과 유사
- **Observer Pattern**: 이벤트 시스템으로 플러그인 간 통신
- **Chain of Responsibility**: 콘텐츠 처리 체인이 CoR과 유사
- **Factory Pattern**: PluginRegistry가 팩토리 역할
- **Decorator Pattern**: 플러그인이 기능을 래핑하여 확장하는 구조
- **Service Locator Pattern**: PluginContext를 통한 서비스 조회

## 참고 자료

- [Plugin-based Architecture (Martin Fowler)](https://martinfowler.com/articles/microservices.html)
- [IntelliJ Plugin Development](https://plugins.jetbrains.com/docs/intellij/basics.html)
- [VS Code Extension API](https://code.visualstudio.com/api)
- [Gradle Custom Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)
- [Java ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)
