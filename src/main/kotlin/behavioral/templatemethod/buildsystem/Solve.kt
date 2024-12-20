package behavioral.templatemethod.buildsystem

class Solve {
    abstract class AppBuilder {
        // 템플릿 메서드
        fun buildApp() {
            println("Starting ${getPlatformName()} build process...")

            // 공통된 빌드 단계들
            setupEnvironment()
            compileCode()
            runLinter()
            runTests()
            createPackage()
            signPackage()

            println("${getPlatformName()} build completed!\n")
        }

        // 추상 메서드 - 하위 클래스에서 반드시 구현
        protected abstract fun getPlatformName(): String
        protected abstract fun compileCode()
        protected abstract fun createPackage()
        protected abstract fun signPackage()

        // 훅 메서드 - 선택적 오버라이드
        protected open fun setupEnvironment() {
            println("Setting up build environment")
        }

        protected open fun runLinter() {
            println("Running code quality checks")
        }

        protected open fun runTests() {
            println("Running platform tests")
        }
    }

    // 안드로이드 구체 클래스
    class AndroidAppBuilder: AppBuilder() {
        override fun getPlatformName() = "Android"

        override fun compileCode() {
            println("Compiling Kotlin/Java code using Gradle")
        }

        override fun createPackage() {
            println("Creating APK package")
        }

        override fun signPackage() {
            println("Signing APK with Android Keystore")
        }

        override fun runLinter() {
            println("Running Android Lint checks")
        }
    }

    // iOS 구현체 클래스
    class IOSAppBuilder: AppBuilder() {
        override fun getPlatformName() = "iOS"

        override fun setupEnvironment() {
            super.setupEnvironment()
            println("Configuring Xcode environment")
        }

        override fun compileCode() {
            println("Compiling Swift code using Xcode")
        }

        override fun createPackage() {
            println("Creating IPA package")
        }

        override fun signPackage() {
            println("Signing IPA with iOS certificate")
        }

        override fun runTests() {
            println("Running XCTest suite")
        }
    }
}

fun main() {
    val builders = listOf(
        Solve.AndroidAppBuilder(),
        Solve.IOSAppBuilder()
    )

    builders.forEach { builder ->
        builder.buildApp()
    }
}