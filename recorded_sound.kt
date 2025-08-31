import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.avutil.*
import org.bytedeco.javacpp.avformat.*
import org.bytedeco.javacpp.avcodec.*
import org.bytedeco.javacpp.swresample.*
import org.bytedeco.javacpp.vosk.*

val MODEL_PATH = "vosk-model-small-ru-0.22"

if (!File(MODEL_PATH).exists()) {
    println("❌ Model folder not found: $MODEL_PATH")
    System.exit(1)
}

val requiredDirs = listOf("am", "conf", "graph", "ivector")
val missing = requiredDirs.filter { !File(MODEL_PATH, it).exists() }
if (missing.isNotEmpty()) {
    println("❌ Missing directories in $MODEL_PATH: $missing")
    System.exit(1)
}

println("✅ Model found, loading...")

val model = Model(MODEL_PATH)
val rec = KaldiRecognizer(model, 16000)

val q = ArrayBlockingQueue<ByteArray>(10)

fun saveText(text: String, filename: String = "recognized.txt") {
    try {
        FileWriter(filename, true).use { writer ->
            writer.write("$text\n")
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun callback(indata: ByteArray) {
    q.put(indata)
}

val inputStream = RawInputStream(16000, 8000, AV_SAMPLE_FMT_S16, 1, ::callback)
inputStream.start()
println("🎤 Speak something... (Ctrl+C to exit)")
while (true) {
    val data = q.take()
    if (rec.AcceptWaveform(BytePointer(data))) {
        val result = rec.Result()
        val text = result.get("text") ?: ""
        if (text.isNotEmpty()) {
            println("➡️ Result: $text")
            saveText(text) // save to file
        }
    } else {
        val partial = rec.PartialResult()
        if (partial.get("partial") != null) {
            println("...interim: ${partial["partial"]}")
        }
    }
}
