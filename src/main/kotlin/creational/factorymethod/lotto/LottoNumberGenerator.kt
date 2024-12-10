package creational.factorymethod.lotto

interface LottoNumberGenerator {
    fun generateLuckyNumbers(count: Int): List<Int>
}