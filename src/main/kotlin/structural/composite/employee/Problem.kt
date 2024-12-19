package structural.composite.employee

/**
 * 문제 코드: 복잡한 조직 구조 관리의 어려움
 *
 * 문제점
 * - 복잡한 계층 구조 관리의 어려움
 * - 개별 객체와 복합 객체의 비일관적인 처리
 * - 재귀적 구조 순회의 복잡성
 * - 코드의 확장성 및 유연성 부족
 */
class Problem {
    class Employee(private val name: String, private val position: String) {
        private val subordinates: MutableList<Employee> = mutableListOf()

        fun addSubordinate(employee: Employee) {
            subordinates.add(employee)
        }

        fun printDetails() {
            println("Name: $name, Position: $position")

            // 재귀적 출력의 복잡성
            if (subordinates.isNotEmpty()) {
                println("Subordinates:")
                for (subordinate in subordinates) {
                    subordinate.printDetails()
                }
            }
        }
    }
}

fun main() {
    val ceo = Problem.Employee("John Doe", "CEO")

    val manager1 = Problem.Employee("Alice", "Manager")
    val manager2 = Problem.Employee("Bob", "Manager")

    val developer1 = Problem.Employee("Charlie", "Developer")
    val developer2 = Problem.Employee("David", "Developer")
    val developer3 = Problem.Employee("Eve", "Developer")

    // 복잡한 계층 구조 생성
    ceo.addSubordinate(manager1)
    ceo.addSubordinate(manager2)

    manager1.addSubordinate(developer1)
    manager1.addSubordinate(developer2)

    manager2.addSubordinate(developer3)

    // 전체 조직 구조 출력
    ceo.printDetails()
}