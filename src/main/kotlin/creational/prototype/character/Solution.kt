package creational.prototype.character

class Solution {
    interface CharacterPrototype {
        fun clone(): CharacterPrototype
    }

    // 장비 클래스
    data class Equipment(
        val type: String,
        val name: String,
        val stats: MutableMap<String, Int>
    ) : Cloneable {
        public override fun clone(): Equipment {
            return Equipment(type, name, stats.toMutableMap())
        }
    }

    // 스킬 클래스
    data class Skill(
        val name: String,
        val damage: Int,
        val cooldown: Int,
        val effects: MutableList<String>
    ) : Cloneable {
        public override fun clone(): Skill {
            return Skill(name, damage, cooldown, effects.toMutableList())
        }
    }

    // 구체적인 캐릭터 클래스
    class GameCharacter(
        var name: String,
        var level: Int,
        private val skills: MutableList<Skill>,
        private val equipments: MutableList<Equipment>,
        private val attributes: MutableMap<String, Int>,
    ) : CharacterPrototype {

        override fun clone(): CharacterPrototype {
            // 깊은 복사 구현
            return GameCharacter(
                name,
                level,
                skills.map { it.clone() }.toMutableList(),
                equipments.map { it.clone() }.toMutableList(),
                attributes.toMutableMap()
            )
        }

        // 캐릭터 커스터마이징 메서드
        fun customize(
            newName: String,
            levelAdjustment: Int = 0,
            newSkills: List<Skill>? = null,
            newEquipment: List<Equipment>? = null
        ): GameCharacter {
            val cloned = clone() as GameCharacter
            cloned.name = newName
            cloned.level += levelAdjustment
            newSkills?.let { cloned.skills.addAll(it) }
            newEquipment?.let { cloned.equipments.addAll(it) }
            return cloned
        }

        fun getStats(): Map<String, Any> {
            return mapOf(
                "name" to name,
                "level" to level,
                "skills" to skills.map { it.name },
                "equipment" to equipments.map { "${it.type}: ${it.name}" },
                "attributes" to attributes,
            )
        }
    }

    // 캐릭터 프로토타입 레지스트리
    object CharacterRegistry {
        private val prototypes = mutableMapOf<String, GameCharacter>()

        fun addPrototype(type: String, prototype: GameCharacter) {
            prototypes[type] = prototype
        }

        fun createCharacter(type: String, newName: String): GameCharacter? {
            return prototypes[type]?.customize(newName)
        }
    }
}

fun main() {
    // 기본 전사 프로토타입 생성
    val warriorPrototype = Solution.GameCharacter(
        name = "Warrior",
        level = 1,
        skills = mutableListOf(
            Solution.Skill("Slash", 10, 3, mutableListOf("Bleeding")),
            Solution.Skill("Block", 0, 5, mutableListOf("Defense Up"))
        ),
        equipments = mutableListOf(
            Solution.Equipment("Weapon", "Basic Sword", mutableMapOf("damage" to 5)),
            Solution.Equipment("Armor", "Leather Armor", mutableMapOf("defense" to 3))
        ),
        attributes = mutableMapOf("STR" to 10, "DEX" to 5, "CON" to 8)
    )

    // 프로토타입 등록
    Solution.CharacterRegistry.addPrototype("warrior", warriorPrototype)

    // 새로운 캐릭터 생성
    val player1 = Solution.CharacterRegistry.createCharacter("warrior", "Player1")
    val player2 = Solution.CharacterRegistry.createCharacter("warrior", "Player2")

    // 캐릭터 커스터마이징
    val copyPlayer1 = player1?.customize(
        player1.name,
        levelAdjustment = 2,
        newSkills = listOf(Solution.Skill("Pierce", 15, 4, mutableListOf("Armor Break")))
    )

    // 각 캐릭터의 상태 출력
    println("Original Prototype stats:")
    println(warriorPrototype.getStats())
    println("\nPlayer1 stats:")
    println(player1?.getStats())
    println("\nPlayer1 Copy stats:")
    println(copyPlayer1?.getStats())
    println("\nPlayer2 stats:")
    println(player2?.getStats())
}