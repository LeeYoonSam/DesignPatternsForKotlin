package structural.composite.filesystem

/**
 * 문제점
 * - 복잡한 트리 구조 관리의 어려움
 * - 개별 객체와 그룹 객체의 불일관한 처리
 * - 재귀적 작업 구현의 복잡성
 * - 객체 구조 변경의 어려움
 * - 타입 안전성 보장 문제
 */
class Problem {
    class File(val name: String, val size: Long)

    class Directory(val name: String) {
        private val files = mutableListOf<File>()
        private val subdirectories = mutableListOf<Directory>()

        fun addFile(file: File) {
            files.add(file)
        }

        fun addDirectory(directory: Directory) {
            subdirectories.add(directory)
        }

        // 크기 계산을 위해 별도의 로직 필요
        fun calculateSize(): Long {
            return files.sumOf { it.size } + subdirectories.sumOf { it.calculateSize() }
        }

        // 검색을 위해 또 다른 로직 필요
        fun search(keyword: String): List<String> {
            val result = mutableListOf<String>()
            if (name.contains(keyword)) {
                result.add(name)
            }
            files.forEach { file ->
                if (file.name.contains(keyword)) {
                    result.add("${name}/${file.name}")
                }
            }
            subdirectories.forEach { dir ->
                dir.search(keyword).forEach { path ->
                    result.add("${name}/${path}")
                }
            }
            return result
        }
    }
}

fun main() {
    val root = Problem.Directory("root")
    val docs = Problem.Directory("docs")

    docs.addFile(Problem.File("document.txt", 100))
    root.addDirectory(docs)

    println("Total size: ${root.calculateSize()}")
    println("Search results: ${root.search("doc")}")
}