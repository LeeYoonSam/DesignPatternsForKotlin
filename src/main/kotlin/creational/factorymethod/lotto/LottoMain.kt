package creational.factorymethod.lotto

fun main() {
    val randomGenerator = LottoNumberGeneratorFactory.createGenerator(GeneratorType.RANDOM)
    println("랜덤 로또 번호: ${randomGenerator.generateLuckyNumbers(3)}")
    (randomGenerator as RandomLottoNumberGenerator).benchmark()

    val patternGenerator = LottoNumberGeneratorFactory.createGenerator(GeneratorType.PATTERN)
    println("패턴기반 로또 번호: ${patternGenerator.generateLuckyNumbers(3)}")
    (patternGenerator as PatternLottoNumberGenerator).benchmark()

    val performanceGenerator = LottoNumberGeneratorFactory.createGenerator(GeneratorType.PERFORMANCE)
    println("퍼포먼스기반 로또 번호: ${performanceGenerator.generateLuckyNumbers(3)}")
    (performanceGenerator as PerformanceLottoNumberGenerator).benchmark()
}