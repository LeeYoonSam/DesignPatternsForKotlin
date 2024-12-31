package creational.builder.html

import creational.builder.html.Solve.*

class Solve {
    class HTML private constructor(
        val title: String,
        val content: String
    ) {
        data class Builder(
            var title: String = "",
            private val head: MutableList<String> = mutableListOf(),
            private val body: MutableList<String> = mutableListOf(),
            private val styles: MutableList<String> = mutableListOf(),
            private val scripts: MutableList<String> = mutableListOf()
        ) {
            fun title(title: String) = apply { this.title = title }

            fun addStyle(css: String) = apply {
                styles.add(css)
            }

            fun addScript(script: String) = apply {
                scripts.add(script)
            }

            fun addMetaTag(name: String, content: String) = apply {
                head.add("""<meta name="$name" content="$content">""")
            }

            fun addHeader(text: String, level: Int = 1) = apply {
                body.add("""<h$level>$text</h$level>""")
            }

            fun addParagraph(text: String) = apply {
                body.add("""<p>$text</p>""")
            }

            fun addLink(text: String, url: String) = apply {
                body.add("""<a href="$url">$text</a>""")
            }

            fun addImage(src: String, alt: String) = apply {
                body.add("""<img src-"$src" alt="$alt">""")
            }

            fun build(): HTML {
                val content = buildString {
                    appendLine("<!DOCTYPE html>")
                    appendLine("<html>")
                    appendLine("<head>")
                    appendLine("<title>$title</title>")

                    if (styles.isNotEmpty()) {
                        appendLine("<style>")
                        styles.forEach { appendLine(it) }
                        appendLine("</style>")
                    }

                    head.forEach { appendLine(it) }

                    appendLine("</head>")
                    appendLine("<body>")

                    body.forEach { appendLine(it) }

                    if (scripts.isNotEmpty()) {
                        scripts.forEach {
                            appendLine("<script>$it</script>")
                        }
                    }

                    appendLine("</body>")
                    appendLine("</html>")
                }

                return HTML(title, content)
            }
        }

        override fun toString(): String {
            return "title: $title\n$content"
        }
    }

    // Director 클래스: 미리 정의된 생성 과정 제공
    class HTMLDocumentDirector {
        fun createBasicDocument(builder: HTML.Builder, title: String, content: String): HTML {
            return builder
                .title(title)
                .addHeader(title)
                .addParagraph(content)
                .build()
        }

        fun createArticle(
            builder: HTML.Builder,
            title: String,
            author: String,
            content: String
        ): HTML {
            return builder
                .title(title)
                .addMetaTag("author", author)
                .addStyle("article { max-width: 800px; margin: 0 auto; }")
                .addHeader(title)
                .addParagraph("By $author")
                .addParagraph(content)
                .addScript("console.log('Article loaded');")
                .build()
        }
    }
}

fun main() {
    val director = HTMLDocumentDirector()

    // 기본 문서 생성
    val basicDoc = director.createBasicDocument(
        builder = HTML.Builder(),
        title = "안녕하세요",
        content = "첫 번째 문서입니다."
    )
    println("Basic Document:")
    println(basicDoc)

    // 아티클 생성
    val article = director.createArticle(
        HTML.Builder(),
        "빌더 패턴 활용하기",
        "홍길동",
        "빌더 패턴은 객체 생성을 단순화합니다."
    )
    println("Article:")
    println(article)

    println("\n----------------------------------\n")

    // 직접 빌더 사용
    val customDoc = HTML.Builder()
        .title("커스텀 문서")
        .addStyle("body { font-family: Arial; }")
        .addHeader("환영합니다", 1)
        .addParagraph("이것은 커스텀 문서입니다.")
        .addLink("자세히 보기", "https://example.com")
        .addImage("sample.jpg", "샘플 이미지")
        .addScript("alert('Welcome!');")
        .build()
    println("Custom Document:")
    println(customDoc)
}