package creational.builder.html

/**
 * 문제점
 * - 생성자 매개변수가 많은 경우
 * - 선택적 매개변수 처리의 어려움
 * - 객체 생성 과정의 복잡성
 * - 불완전한 객체 생성 가능성
 */
class Problem {
    // 문제가 있는 코드: 복잡한 생성자와 많은 매개변수
    class HTMLDocument(
        val title: String,
        val header: String,
        val paragraphs: List<String>,
        val links: List<Pair<String, String>>,
        val images: List<Triple<String, String, String>>,
        val footer: String,
        val css: String,
        val javascript: String,
    ) {
        override fun toString(): String {
            // 복잡한 HTML 생성 로직...
            return "HTML Document with title: $title"
        }
    }
}

fun main() {
    // 생성자 사용이 복잡하고 가독성이 떨어짐
    val doc = Problem.HTMLDocument(
        "제목",
        "헤더",
        listOf("단락1", "단락2"),
        listOf(Pair("구글", "https://google.com")),
        listOf(Triple("로고", "logo.png", "회사 로고")),
        "푸터",
        "body { color: black; }",
        "console.log('loaded');"
    )

    println(doc)
}