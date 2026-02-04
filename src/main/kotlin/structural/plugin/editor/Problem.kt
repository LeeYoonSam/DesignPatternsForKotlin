package structural.plugin.editor

/**
 * Plugin Architecture Pattern - 문제 상황
 *
 * 텍스트 에디터 앱을 개발하고 있습니다.
 * 마크다운 변환, 코드 하이라이팅, 맞춤법 검사, 테마 변경 등
 * 다양한 기능을 지원해야 하는데,
 * 모든 기능이 에디터 클래스 하나에 하드코딩되어 있습니다.
 */

// ❌ 문제 1: 모든 기능이 하나의 클래스에 하드코딩
class MonolithicEditor {
    private var content: String = ""
    private var theme: String = "light"

    fun processContent(text: String): String {
        var result = text

        // 마크다운 변환 - 에디터가 직접 처리
        result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        result = result.replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
        result = result.replace(Regex("^# (.+)", RegexOption.MULTILINE), "<h1>$1</h1>")
        result = result.replace(Regex("^## (.+)", RegexOption.MULTILINE), "<h2>$1</h2>")

        // 코드 하이라이팅 - 에디터가 직접 처리
        result = result.replace(
            Regex("```(\\w+)\\n([\\s\\S]*?)```"),
            "<pre class=\"language-$1\"><code>$2</code></pre>"
        )

        // 맞춤법 검사 - 에디터가 직접 처리
        val typos = mapOf("teh" to "the", "adn" to "and", "recieve" to "receive")
        for ((wrong, correct) in typos) {
            result = result.replace(wrong, correct)
        }

        // 이모지 변환 - 에디터가 직접 처리
        result = result.replace(":smile:", "😊")
        result = result.replace(":heart:", "❤️")
        result = result.replace(":thumbsup:", "👍")

        // 자동 링크 감지 - 에디터가 직접 처리
        result = result.replace(
            Regex("(https?://\\S+)"),
            "<a href=\"$1\">$1</a>"
        )

        return result
    }

    fun setTheme(themeName: String) {
        // 테마도 하드코딩으로 처리
        theme = when (themeName) {
            "dark" -> "dark"
            "solarized" -> "solarized"
            "monokai" -> "monokai"
            else -> "light"
        }
    }

    fun getAvailableThemes(): List<String> = listOf("light", "dark", "solarized", "monokai")

    fun getWordCount(): Int {
        return content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }

    fun getCharacterCount(): Int = content.length

    // 새 기능을 추가하려면 이 클래스를 직접 수정해야 함
    // fun addNewFeature() { ... }
}

// ❌ 문제 2: 기능 추가/제거 시 에디터 클래스 전체를 수정해야 함
class EditorWithFlags {
    private var enableMarkdown = true
    private var enableCodeHighlight = true
    private var enableSpellCheck = true
    private var enableEmoji = true
    private var enableAutoLink = true
    private var enableWordCount = true
    // 기능이 추가될 때마다 플래그 추가...
    // private var enableNewFeature = false

    fun processContent(text: String): String {
        var result = text

        // 플래그 기반으로 조건 분기가 난무
        if (enableMarkdown) {
            result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            result = result.replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
        }

        if (enableCodeHighlight) {
            result = result.replace(
                Regex("```(\\w+)\\n([\\s\\S]*?)```"),
                "<pre class=\"language-$1\"><code>$2</code></pre>"
            )
        }

        if (enableSpellCheck) {
            val typos = mapOf("teh" to "the", "adn" to "and")
            for ((wrong, correct) in typos) {
                result = result.replace(wrong, correct)
            }
        }

        if (enableEmoji) {
            result = result.replace(":smile:", "😊")
        }

        if (enableAutoLink) {
            result = result.replace(Regex("(https?://\\S+)"), "<a href=\"$1\">$1</a>")
        }

        // 새 기능을 추가하면 여기에 또 다른 if 블록 추가...
        // if (enableNewFeature) { ... }

        return result
    }

    fun toggleFeature(feature: String, enabled: Boolean) {
        // 문자열 비교로 기능 토글 - 타입 안전하지 않음
        when (feature) {
            "markdown" -> enableMarkdown = enabled
            "codeHighlight" -> enableCodeHighlight = enabled
            "spellCheck" -> enableSpellCheck = enabled
            "emoji" -> enableEmoji = enabled
            "autoLink" -> enableAutoLink = enabled
            "wordCount" -> enableWordCount = enabled
            // 새 기능마다 case 추가...
            else -> println("Unknown feature: $feature")
        }
    }
}

// ❌ 문제 3: 제3자가 기능을 추가할 수 없음
class ThirdPartyProblem {
    fun demonstrate() {
        val editor = MonolithicEditor()

        // 외부 라이브러리나 제3자가 새 기능을 추가하고 싶다면?
        // → 에디터 소스코드를 직접 수정해야 함 (OCP 위반)
        // → 에디터를 상속받아 재정의? → 깨지기 쉬운 기반 클래스 문제

        // 예: LaTeX 수식 렌더링을 추가하고 싶다면?
        // → MonolithicEditor의 processContent()를 수정해야 함
        // → 다른 기능과의 충돌 위험
        // → 에디터 업데이트 시 머지 충돌

        // 예: 커스텀 테마를 추가하고 싶다면?
        // → setTheme()과 getAvailableThemes()를 수정해야 함
        // → when 블록에 새 case 추가

        println("제3자 기능 확장이 불가능한 구조")
    }
}

// ❌ 문제 4: 기능 간 의존성 관리 불가
class DependencyProblem {
    fun demonstrate() {
        // 코드 하이라이팅이 마크다운 처리 이후에 실행되어야 한다면?
        // → processContent() 내부의 코드 순서에 의존
        // → 순서를 변경하면 다른 기능이 깨질 수 있음
        // → 실행 순서를 동적으로 변경할 수 없음

        // 맞춤법 검사가 특정 언어에서만 동작해야 한다면?
        // → processContent()에 또 다른 조건 분기 추가
        // → 조건이 복잡해질수록 유지보수 어려움

        println("기능 간 의존성과 실행 순서를 관리할 수 없음")
    }
}

// ❌ 문제 5: 테스트 어려움
class TestingProblem {
    fun demonstrate() {
        val editor = MonolithicEditor()

        // 마크다운 변환만 테스트하고 싶지만
        // processContent()가 모든 기능을 한번에 실행
        val result = editor.processContent("**bold** :smile: https://example.com")

        // 마크다운 결과만 검증하려 해도 이모지, 링크 변환이 함께 적용됨
        // → 개별 기능을 격리해서 테스트할 수 없음
        // → 특정 기능의 버그를 찾기 어려움
        // → 모킹이나 스텁 적용 불가

        println("Result: $result")
        println("개별 기능을 격리해서 테스트할 수 없음")
    }
}

fun main() {
    println("=== Plugin Architecture Pattern - 문제 상황 ===\n")

    // 문제 1: 모놀리식 에디터
    val editor = MonolithicEditor()
    val processed = editor.processContent(
        "**Hello** *world*\n# Title\n:smile: https://kotlin.org"
    )
    println("모놀리식 처리 결과: $processed")
    println("→ 모든 기능이 하나의 클래스에 하드코딩\n")

    // 문제 2: 플래그 기반
    val flagEditor = EditorWithFlags()
    flagEditor.toggleFeature("emoji", false)
    flagEditor.toggleFeature("unknownFeature", true) // 타입 안전하지 않음
    println("→ 기능 토글을 위해 문자열 비교, 새 기능마다 클래스 수정 필요\n")

    // 문제 3: 제3자 확장 불가
    ThirdPartyProblem().demonstrate()
    println()

    // 문제 4: 의존성 관리 불가
    DependencyProblem().demonstrate()
    println()

    // 문제 5: 테스트 어려움
    TestingProblem().demonstrate()

    println("\n핵심 문제:")
    println("• OCP(개방-폐쇄 원칙) 위반 - 확장을 위해 기존 코드를 수정해야 함")
    println("• SRP(단일 책임 원칙) 위반 - 에디터가 모든 기능을 직접 담당")
    println("• 제3자 확장 불가 - 외부에서 기능을 추가/제거할 수 없음")
    println("• 기능 격리 불가 - 개별 기능을 독립적으로 테스트/관리할 수 없음")
}
