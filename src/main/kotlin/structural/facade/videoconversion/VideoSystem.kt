package structural.facade.videoconversion

class VideoFile(private val name: String) {
    fun decode(): String = "Decoding video file: $name"
}

class AudioMixer {
    fun mix(audioData: String): String = "Mixing audio: $audioData"
}

class BitrateReader {
    fun read(fileName: String, sourceCodec: String): String =
        "Reading bitrate for $fileName with codec $sourceCodec"

    fun convert(buffer: String, destinationCodec: String): String =
        "Converting $buffer to $destinationCodec"
}

class CodecFactory {
    fun extract(file: String): String =
        when {
            file.endsWith(".mp4") -> "mp4"
            file.endsWith(".ogg") -> "ogg"
            else -> "unknown"
        }
}

class MPEG4CompressionCodec {
    fun compress(video: String): String = "Compressing video to MPEG4: $video"
}

class OggCompressionCodec {
    fun compress(video: String): String = "Compressing video to OGG: $video"
}