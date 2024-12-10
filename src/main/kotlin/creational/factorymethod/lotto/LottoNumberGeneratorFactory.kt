package creational.factorymethod.lotto

object LottoNumberGeneratorFactory {
    fun createGenerator(type: GeneratorType): LottoNumberGenerator {
        return when (type) {
            GeneratorType.RANDOM -> RandomLottoNumberGenerator()
            GeneratorType.PATTERN -> PatternLottoNumberGenerator()
            GeneratorType.PERFORMANCE -> PerformanceLottoNumberGenerator()
        }
    }
}