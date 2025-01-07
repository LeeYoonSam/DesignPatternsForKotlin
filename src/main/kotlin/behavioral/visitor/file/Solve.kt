package behavioral.visitor.file

class Solve {
    // Visitor 인터페이스
    interface FileSystemVisitor {
        fun visit(file: File)
        fun visit(directory: Directory)
    }

    // Element 인터페이스
    interface FileSystemElement {
        fun accept(visitor: FileSystemVisitor)
        fun getName(): String
        fun getPermissions(): String
    }

    // Concrete Elements
    class File(
        private val name: String,
        private val size: Long,
        private val permissions: String
    ): FileSystemElement {
        fun getSize(): Long = size

        override fun getName(): String = name
        override fun getPermissions(): String = permissions

        override fun accept(visitor: FileSystemVisitor) {
            visitor.visit(this)
        }
    }

    class Directory(
        private val name: String,
        private val permissions: String,
        private val contents: MutableList<FileSystemElement> = mutableListOf()
    ): FileSystemElement {
        override fun getName(): String = name
        override fun getPermissions(): String = permissions

        fun add(element: FileSystemElement) {
            contents.add(element)
        }

        fun getContents(): List<FileSystemElement> = contents

        override fun accept(visitor: FileSystemVisitor) {
            visitor.visit(this)
            contents.forEach { it.accept(visitor) }
        }
    }

    // Concrete Visitors
    class PrintVisitor : FileSystemVisitor {
        override fun visit(file: File) {
            println("File: ${file.getName()}, Size: ${file.getSize()} bytes, Permissions: ${file.getPermissions()}")
        }

        override fun visit(directory: Directory) {
            println("Directory: ${directory.getName()}, Permissions: ${directory.getPermissions()}")
        }
    }

    class JsonVisitor : FileSystemVisitor {
        private val json = StringBuilder()
        private var indent = 0

        override fun visit(file: File) {
            json.append("   ".repeat(indent))
                .append("""{"type":"file","name":"${file.getName()}","size":${file.getSize()},"permissions":"${file.getPermissions()}"}""")
                .append(",\n")
        }

        override fun visit(directory: Directory) {
            json.append("   ".repeat(indent))
                .append("""{"type":"directory","name":"${directory.getName()}","permissions":"${directory.getPermissions()}","contents":[""")
                .append("\n")
            indent++
        }

        fun getJson(): String {
            return json.toString().removeSuffix(",\n")
        }
    }

    class XmlVisitor : FileSystemVisitor {
        private val xml = StringBuilder()
        private var indent = 0

        override fun visit(file: File) {
            xml.append("  ".repeat(indent))
                .append("""<file name="${file.getName()}" size="${file.getSize()}" permissions="${file.getPermissions()}" />""")
                .append("\n")
        }

        override fun visit(directory: Directory) {
            xml.append("  ".repeat(indent))
                .append("""<directory name="${directory.getName()}" permissions="${directory.getPermissions()}">""")
                .append("\n")
            indent++
        }

        fun getXml(): String {
            return xml.toString()
        }
    }
}

fun main() {
    val root = Solve.Directory("root", "rwxr-xr-x")
    val docs = Solve.Directory("docs", "rwxr-xr-x")
    val config = Solve.File("config.json", 1024, "rw-r--r--")
    val readme = Solve.File("README.md", 2048, "rw-r--r--")

    docs.add(readme)
    root.add(docs)
    root.add(config)

    println("File System Structure:")
    val printVisitor = Solve.PrintVisitor()
    root.accept(printVisitor)

    println("\nJSON representation:")
    val jsonVisitor = Solve.JsonVisitor()
    root.accept(jsonVisitor)
    println(jsonVisitor.getJson())

    println("\nXML representation:")
    val xmlVisitor = Solve.XmlVisitor()
    root.accept(xmlVisitor)
    println(xmlVisitor.getXml())
}