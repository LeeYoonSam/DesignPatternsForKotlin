package structural.composite.employee

class Solve {
    // 조직 컴포넌트 인터페이스
    interface OrganizationComponent {
        fun getName(): String
        fun getPosition(): String
        fun printDetails(depth: Int = 0)
    }

    // 개별 직원 클래스
    class IndividualEmployee(
        private val name: String,
        private val position: String
    ) : OrganizationComponent {
        override fun getName() = name

        override fun getPosition() = position

        override fun printDetails(depth: Int) {
            val indent = "  ".repeat(depth)
            println("$indent- $name ($position)")
        }
    }

    // 컴포지트 클래스 (관리자 도는 그룹)
    class EmployeeComposite(
        private val name: String,
        private val position: String
    ) : OrganizationComponent {
        private val subordinates = mutableListOf<OrganizationComponent>()

        override fun getName() = name
        override fun getPosition() = position

        fun addSubordinate(component: OrganizationComponent) {
            subordinates.add(component)
        }

        override fun printDetails(depth: Int) {
            val indent = "  ".repeat(depth)
            println("$indent+ $name ($position)")

            subordinates.forEach { it.printDetails(depth + 1) }
        }
    }
}

fun main() {
    // 최상위 CEO
    val ceo = Solve.EmployeeComposite("John Doe", "CEO")

    // 매니저 그룹
    val managementTeam = Solve.EmployeeComposite("Management Team", "Management")
    val manager1 = Solve.EmployeeComposite("Alice", "Department Manager")
    val manager2 = Solve.EmployeeComposite("Bob", "Department Manager")

    // 개발 팀
    val developmentTeam = Solve.EmployeeComposite("Development Team", "Technical")
    val developer1 = Solve.IndividualEmployee("Charlie", "Senior Developer")
    val developer2 = Solve.IndividualEmployee("David", "Developer")
    val developer3 = Solve.IndividualEmployee("Eve", "Junior Developer")

    // 디자인 팀
    val designTeam = Solve.EmployeeComposite("Design Team", "Art")
    val designer1 = Solve.IndividualEmployee("Lin", "Senior Designer")
    val designer2 = Solve.IndividualEmployee("Michel", "Designer")
    val designer3 = Solve.IndividualEmployee("Jack", "Junior Designer")

    // 조직 구조 조립
    ceo.addSubordinate(managementTeam)
    managementTeam.addSubordinate(manager1)
    managementTeam.addSubordinate(manager2)

    ceo.addSubordinate(developmentTeam)
    developmentTeam.addSubordinate(developer1)
    developmentTeam.addSubordinate(developer2)
    developmentTeam.addSubordinate(developer3)

    ceo.addSubordinate(designTeam)
    designTeam.addSubordinate(designer1)
    designTeam.addSubordinate(designer2)
    designTeam.addSubordinate(designer3)

    // 전체 조직 구조 출력
    ceo.printDetails()
}
