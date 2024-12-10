package creational.factorymethod.lotto

import kotlin.random.Random

class PerformanceLottoNumberGenerator : LottoNumberGenerator {
    companion object {
        // 상수 정의
        private const val MIN_NUMBER = 1
        private const val MAX_NUMBER = 45
        private const val DEFAULT_COUNT = 6
    }


    override fun generateLuckyNumbers(count: Int): List<Int> {
        return generateNumbers(count).toList()
    }

    // 고성능 로또 번호 생성 메서드
    private fun generateNumbers(count: Int = DEFAULT_COUNT): IntArray {
        require(count in 1..MAX_NUMBER) { "추출할 번호의 개수는 1에서 45 사이여야 합니다." }

        // 1~45 배열 생성
        val numbers = IntArray(MAX_NUMBER) { it + 1 }

        /**
         * Fisher-Yates 셔플 알고리즘 사용
         *
         * - 배열 전체를 한 번에 무작위로 섞음
         * - 시간 복잡도: O(n)
         * - 메모리 할당과 반복 최소화
         */
        for (i in numbers.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val temp = numbers[i]
            numbers[i] = numbers[j]
            numbers[j] = temp
        }

        // 처음 count개 추출 후 정렬
        return numbers.sliceArray(0 until count).apply { sort() }
    }

    // 벤치마크용 메서드 (성능 비교)
    fun benchmark(iterations: Int = 10000) {
        val start = System.nanoTime()
        repeat(iterations) {
            generateNumbers()
        }
        val end = System.nanoTime()

        println("$iterations 번 반복 실행 시간: ${(end - start) / 1_000_000.0} ms")
    }
}