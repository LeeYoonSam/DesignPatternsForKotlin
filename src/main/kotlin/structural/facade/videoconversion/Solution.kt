package structural.facade.videoconversion

// Facade 클래스
class VideoConversionFacade {
    fun convertVideo(fileName: String, format: String): String {
        println("VideoConversionFacade: beginning conversion...")

        val file = VideoFile(fileName)
        val codecFactory = CodecFactory()
        val sourceCodec = codecFactory.extract(fileName)

        val bitrateReader = BitrateReader()
        val buffer = bitrateReader.read(fileName, sourceCodec)

        var result: String = when (format.lowercase()) {
            "mp4" -> {
                val destinationCodec = MPEG4CompressionCodec()
                destinationCodec.compress(buffer)
            }
            "ogg" -> {
                val destinationCodec = OggCompressionCodec()
                destinationCodec.compress(buffer)
            }
            else -> throw RuntimeException("Unsupported format")
        }

        val mixer = AudioMixer()
        result = mixer.mix(result)

        println("VideoConversionFacade: conversion completed")
        return result
    }
}

fun main() {
    val converter = VideoConversionFacade()

    // 단순화된 인터페이스를 통한 비디오 변환
    val result = converter.convertVideo("birthday.mp4", "ogg")
    println("Final result: $result")

    // 다른 형식으로도 쉽게 변환 가능
    val anotherResult = converter.convertVideo("vacation.ogg", "mp4")
    println("Another result: $anotherResult")
}