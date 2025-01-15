package structural.composite.filesystem

import java.time.LocalDateTime

class Solution {
    // 파일 시스템 요소 인터페이스
    interface FileSystemElement {
        val name: String
        val created: LocalDateTime
        val modified: LocalDateTime
        val type: String

        fun size(): Long
        fun search(keyword: String): List<String>
        fun list(indent: String = ""): String
        fun accept(visitor: FileSystemVisitor)
    }

    // 방문자 패턴 통합
    interface FileSystemVisitor {
        fun visitFile(file: File)
        fun visitDirectory(directory: Directory)
    }

    // 파일 클래스
    class File(
        override val name: String,
        private val content: ByteArray,
        override val created: LocalDateTime = LocalDateTime.now(),
        override val modified: LocalDateTime = created
    ) : FileSystemElement {
        override val type: String = "FILE"

        override fun size(): Long = content.size.toLong()

        override fun search(keyword: String): List<String> =
            if (name.contains(keyword)) listOf(name) else emptyList()

        override fun list(indent: String): String =
            "$indent- $name (${size()} bytes)"

        override fun accept(visitor: FileSystemVisitor) {
            visitor.visitFile(this)
        }
    }

    // 디렉토리 클래스
    class Directory(
        override val name: String,
        override val created: LocalDateTime = LocalDateTime.now(),
        override val modified: LocalDateTime = created
    ) : FileSystemElement {
        override val type: String = "DIRECTORY"
        private val children = mutableListOf<FileSystemElement>()

        fun add(element: FileSystemElement) {
            children.add(element)
        }

        fun remove(element: FileSystemElement) {
            children.remove(element)
        }

        fun findDirectory(directory: String): Directory? {
            return children
                .filterIsInstance<Directory>()
                .find { it.name == directory }
        }

        override fun size(): Long = children.sumOf { it.size() }

        override fun search(keyword: String): List<String> {
            val result = mutableListOf<String>()
            if (name.contains(keyword)) {
                result.add(name)
            }
            children.forEach { child ->
                child.search(keyword).forEach { path ->
                    result.add("$name/$path")
                }
            }
            return result
        }

        override fun list(indent: String): String {
            val sb = StringBuilder("$indent+ $name/\n")
            children.forEach { child ->
                sb.append(child.list("$indent  ")).append("\n")
            }
            return sb.toString().trimEnd()
        }

        override fun accept(visitor: FileSystemVisitor) {
            visitor.visitDirectory(this)
            children.forEach { it.accept(visitor) }
        }
    }

    // 통계 수집 방문자
    class StatisticsVisitor : FileSystemVisitor {
        var totalFiles = 0
            private set
        var totalDirectories = 0
            private set
        var totalSize = 0L
            private set

        override fun visitFile(file: File) {
            totalFiles++
            totalSize += file.size()
        }

        override fun visitDirectory(directory: Directory) {
            totalDirectories++
        }
    }

    // 파일 시스템 관리자
    class FileSystem {
        private val root = Directory("root")

        fun createFile(path: String, content: ByteArray) {
            val parts = path.split("/")
            var current = root

            // 경로 생성
            for (i in 1 until parts.size - 1) {
                val directoryName = parts[i]
                current = current.findDirectory(directoryName) ?: Directory(directoryName).also { current.add(it) }
            }

            current.add(File(parts.last(), content))
        }

        fun list(): String = root.list()

        fun getStatistics(): Map<String, Any> {
            val visitor = StatisticsVisitor()
            root.accept(visitor)
            return mapOf(
                "totalFiles" to visitor.totalFiles,
                "totalDirectories" to visitor.totalDirectories,
                "totalSize" to visitor.totalSize
            )
        }
    }
}
// 개선된 코드의 실행
fun main() {
    val fileSystem = Solution.FileSystem()

    // 파일 시스템 구조 생성
    fileSystem.createFile("root/docs/document1.txt", "Hello, World!".toByteArray())
    fileSystem.createFile("root/docs/document2.txt", "Composite Pattern".toByteArray())
    fileSystem.createFile("root/images/photo.jpg", ByteArray(1024))
    fileSystem.createFile("root/src/main.kt", "fun main() {}".toByteArray())

    // 파일 시스템 구조 출력
    println("File System Structure:")
    println(fileSystem.list())

    // 통계 정보 출력
    println("\nFile System Statistics:")
    fileSystem.getStatistics().forEach { (key, value) ->
        println("$key: $value")
    }
}