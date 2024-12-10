package creational.factorymethod.lotto

import kotlin.random.Random

class PatternLottoNumberGenerator : LottoNumberGenerator {
    override fun generateLuckyNumbers(count: Int): List<Int> {
        require(count in 1..45) { "추출할 번호의 개수는 1에서 45 사이어야 합니다." }

        val result = mutableSetOf<Int>()

        while (result.size < count) {
            // 패턴 로직: 10의 배수와 랜덤 번호 혼합
            val number = when {
                result.size % 2 == 0 -> generatePatternNumber()
                else -> Random.nextInt(1, 46)
            }

            // 중복 및 범위 체크
            if (number in 1..45 && number !in result) {
                result.add(number)
            }
        }

        return result.sorted()
    }

    // 패턴 기반 번호 생성 메서드
    private fun generatePatternNumber(): Int {
        val patternBases = listOf(10, 20, 30, 40)
        return patternBases.random() + Random.nextInt(1, 10)
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