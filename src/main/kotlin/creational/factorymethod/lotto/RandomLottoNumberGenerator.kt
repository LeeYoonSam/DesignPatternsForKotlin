package creational.factorymethod.lotto

import kotlin.random.Random

class RandomLottoNumberGenerator : LottoNumberGenerator {
    override fun generateLuckyNumbers(count: Int): List<Int> {
        require(count in 1..45) { "추출할 번호의 개수는 1에서 45 사이어야 합니다." }

        return generateSequence {
            Random.nextInt(1, 46)
        }.distinct()
            .take(count)
            .sorted()
            .toList()
    }

    // 벤치마크용 메서드 (성능 비교)
    fun benchmark(iterations: Int = 10000) {
        val start = System.nanoTime()
        repeat(iterations) {
            generateLuckyNumbers(6)
        }
        val end = System.nanoTime()

        println("$iterations 번 반복 실행 시간: ${(end - start) / 1_000_000.0} ms")
    }
}