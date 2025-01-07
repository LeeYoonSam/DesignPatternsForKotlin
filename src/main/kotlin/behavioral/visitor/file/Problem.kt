package behavioral.visitor.file

/**
 * 문제점
 * - 새로운 기능 추가 시 클래스 수정 필요
 * - 객체 타입별 처리 로직 분산
 * - 조건문 증가
 * - 기능 확장의 어려움
 * - 단일 책임 원칙 위반
 */
class Problem {
    sealed class FileSystemElement {
        abstract fun getSize(): Long
        abstract fun getName(): String
        abstract fun getPermissions(): String
    }

    class File(
        private val name: String,
        private val size: Long,
        private val permissions: String
    ) : FileSystemElement() {
        override fun getSize(): Long = size
        override fun getName(): String = name
        override fun getPermissions(): String = permissions

        fun printInfo() {
            println("File: $name, Size: $size bytes, Permissions: $permissions")
        }

        fun getJson(): String {
            return """{"type":"file","name":"$name","size":"$size","permissions":"$permissions"}"""
        }

        fun getXml(): String{
            return "<file name=\"$name\" size=\"$size\" permissions=\"$permissions\" />"
        }
    }

    class Directory(
        private val name: String,
        private val permissions: String,
        private val contents: MutableList<FileSystemElement> = mutableListOf()
    ) : FileSystemElement() {
        override fun getSize(): Long = contents.sumOf { it.getSize() }
        override fun getName(): String = name
        override fun getPermissions(): String = permissions

        fun add(element: FileSystemElement) {
            contents.add(element)
        }

        fun printInfo() {
            println("Directory: $name, Total Size: ${getSize()} bytes, Permissions: $permissions")
            contents.forEach {
                when (it) {
                    is File -> it.printInfo()
                    is Directory -> it.printInfo()
                }
            }
        }

        fun getJson(): String {
            val contentJson = contents.joinToString(",") {
                when (it) {
                    is File -> it.getJson()
                    is Directory -> it.getJson()
                }
            }
            return """{"type":"directory","name":"$name","size":${getSize()},"permissions":"$permissions","contents":[$contentJson]}"""
        }

        fun getXml(): String {
            val contentXml = contents.joinToString("") {
                when (it) {
                    is File -> it.getXml()
                    is Directory -> it.getXml()
                }
            }
            return "<directory name=\"$name\" size=\"${getSize()}\" permissions=\"$permissions\">$contentXml</directory>"
        }
    }
}

fun main() {
    val root = Problem.Directory("root", "rwxr-xr-x")
    val docs = Problem.Directory("docs", "rwxr-xr-x")
    val config = Problem.File("config.json", 1024, "rw-r--r--")
    val readme = Problem.File("README.md", 2048, "rw-r--r--")

    docs.add(readme)
    root.add(docs)
    root.add(config)

    println("File System Structure:")
    root.printInfo()

    println("\nJSON representation:")
    println(root.getJson())

    println("\nXML representation:")
    println(root.getXml())
}