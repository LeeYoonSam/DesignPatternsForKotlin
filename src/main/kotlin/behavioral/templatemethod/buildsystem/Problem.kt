package behavioral.templatemethod.buildsystem

/**
 * 문제 코드: 중복된 알고리즘 구조
 *
 * 문제점
 * - 유사한 알고리즘 구현의 중복
 * - 코드 재사용성 부족
 * - 알고리즘 구조 변경 시 여러 클래스 수정 필요
 * - 유지보수의 어려움
 */
class Problem {
    class AndroidBuilder {
        fun buildApp() {
            println("Building Android App")
            println("1. Compiling Android Code")
            println("2. Running Android Lint")
            println("3. Running Android Tests")
            println("4. Creating APK")
            println("5. Signing APK")
        }
    }

    class IOSBuilder {
        fun buildApp() {
            println("Building iOS App")
            println("1. Compiling Swift Code")
            println("2. Running SwiftLint")
            println("3. Running iOS Tests")
            println("4. Creating IPA")
            println("5. Signing IPA")
        }
    }
}

fun main() {
    println("=== Android 빌드 프로세스 ===")
    val androidBuilder = Problem.AndroidBuilder()
    androidBuilder.buildApp()

    println("=== iOS 빌드 프로세스 ===")
    val iosBuilder = Problem.IOSBuilder()
    iosBuilder.buildApp()
}