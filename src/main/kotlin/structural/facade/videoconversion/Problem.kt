package structural.facade.videoconversion

fun main() {
    // 비디오 변환 작업을 위한 복잡한 프로세스
    val fileName = "birthday.mp4"
    val sourceFile = VideoFile(fileName)
    val codecFactory = CodecFactory()
    val sourceCodec = codecFactory.extract(fileName)

    val bitrateReader = BitrateReader()
    val buffer = bitrateReader.read(fileName, sourceCodec)

    var result: String = when (sourceCodec) {
        "mp4" -> {
            val destinationCodec = MPEG4CompressionCodec()
            destinationCodec.compress(buffer)
        }
        "ogg" -> {
            val destinationCodec = OggCompressionCodec()
            destinationCodec.compress(buffer)
        }
        else -> throw RuntimeException("Unsupported codec")
    }

    val mixer = AudioMixer()
    result = mixer.mix(result)

    println("Final result: $result")
}