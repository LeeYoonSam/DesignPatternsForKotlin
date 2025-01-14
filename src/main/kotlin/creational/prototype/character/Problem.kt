package creational.prototype.character

/**
 * 문제점
 * - 복잡한 객체 생성 비용
 * - 객체 초기화 오버헤드
 * - 상태를 가진 객체 복제의 어려움
 * - 깊은 복사와 얕은 복사 구분
 * - 순환 참조 처리
 */
class Problem {
    class GameCharacter(
        val name: String,
        val level: Int,
        val skills: MutableList<String>,
        val equipment: MutableMap<String, String>
    ) {
        // 새로운 캐릭터 생성 시 모든 속성을 다시 설정해야 함
        fun createSimilarCharacter(newName: String): GameCharacter {
            return GameCharacter(
                newName,
                this.level,
                this.skills,    // 얕은 복사 문제 발생
                this.equipment  // 얕은 복사 문제 발생
            )
        }
    }
}

fun main() {
    val originalCharacter = Problem.GameCharacter(
        name = "Warrior",
        level = 10,
        skills = mutableListOf("Slash", "Block"),
        equipment = mutableMapOf("Weapon" to "Sword", "Armor" to "Plate")
    )

    val newCharacter = originalCharacter.createSimilarCharacter("Warrior2")
    // 얕은 복사로 인해 원본 캐릭터의 장비와 스킬이 변경됨
    newCharacter.skills.add("Pierce")
    newCharacter.equipment["Weapon"] = "Axe"

    println("Original character's skills were also modified: ${originalCharacter.skills}")
    println("Original character's equipment was also modified: ${originalCharacter.equipment}")
}