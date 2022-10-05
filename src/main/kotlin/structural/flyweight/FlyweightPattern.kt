package structural.flyweight

import util.divider
import util.linebreak

data class DogSample1(
    val name: String,   // <20bytes ,upto 20 chars
    val age: Int,       // 8bytes   ,64bit integer
    val gender: String, // 1bytes
    val breed: String,  // 2bytes   ,upto 65k breeds
    val DNA: String     // MBytes
) {
    override fun toString(): String {
        return "$name, $age, $DNA"
    }
}

data class DogSample2(
    val name: String,   // <20bytes ,upto 20 chars
    val age: Int,       // 8bytes   ,64bit integer
    val gender: String, // 1bytes
    val breed: String,  // 2bytes   ,upto 65k breeds
) {
    val DNAseq = "ATAGGCTTACCGATGG...."

    override fun toString(): String {
        return "$name, $age, $DNAseq"
    }
}

data class DogBreedDNA(
    val breed: String,
    val DNA: String
) {
    override fun toString(): String {
        return DNA
    }
}

val DNATable = hashMapOf<String, DogBreedDNA>()

data class Dog(
    val name: String,   // <20bytes ,upto 20 chars
    val age: Int,       // 8bytes   ,64bit integer
    val gender: String, // 1bytes
    val breed: String,  // 2bytes   ,upto 65k breeds
) {
    init {
        if (!DNATable.containsKey(breed)) {
            throw RuntimeException("$breed is not in DNATable")
        }
    }

    companion object {
        var breedDNA: DogBreedDNA? = null

        fun addDNA(breed: String, DNA: String) {
            val breedDNA = DogBreedDNA(breed, DNA)
            this.breedDNA = breedDNA
            DNATable[breed] = breedDNA
        }
    }

    override fun toString(): String {
        return "$name, $age, ${DNATable[breed]}"
    }
}

fun main() {
    divider("기본")
    val choco = DogSample1("choco", 2, "male", "shihTzu", "ATAGSDGQRGGASDDVBB1010..")
    val baduk = DogSample1("baduk", 3, "female", "jinDo", "ATAGSDGQRGGASDDVBB2020..")

    println(choco)
    println(baduk)

    linebreak()
    divider("클래스에 static DNA 정보")

    val choco2 = DogSample2("choco", 2, "male", "shihTzu")
    val baduk2 = DogSample2("baduk", 3, "female", "jinDo")

    println(choco2)
    println(baduk2)

    linebreak()
    divider("DNA 테이블 활용")

    Dog.addDNA("shihTzu", "ATAGSDGQRGGASDDVBB1010")
    Dog.addDNA("jinDo", "ATAGSDGQRGGASDDVBB2020")

    val choco3 = Dog("choco", 2, "male", "shihTzu")
    val baduk3 = Dog("baduk", 3, "female", "jinDo")

    println(choco3)
    println(baduk3)
}