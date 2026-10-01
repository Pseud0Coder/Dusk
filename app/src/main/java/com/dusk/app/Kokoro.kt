package com.dusk.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class KokoroVoice(val sid: Int, val name: String)

/**
 * Kokoro-82M (Apache 2.0), running fully on the phone through sherpa-onnx.
 * The int8 English model is a one-time download, then works offline.
 */
object Kokoro {
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2"
    private const val NAME = "kokoro-int8-en-v0_19"
    const val SIZE_MB = 103

    /** Speaker ids for kokoro-en-v0_19. */
    val voices = listOf(
        KokoroVoice(1, "Bella"), KokoroVoice(3, "Sarah"), KokoroVoice(4, "Sky"),
        KokoroVoice(6, "Michael"), KokoroVoice(7, "Emma"), KokoroVoice(9, "George"),
    )

    /** Download progress 0..1 while downloading, null otherwise. */
    var progress by mutableStateOf<Float?>(null)
        private set

    private var tts: OfflineTts? = null

    private fun root(ctx: Context) = File(ctx.filesDir, "kokoro")
    private fun dir(ctx: Context) = File(root(ctx), NAME)

    fun ready(ctx: Context): Boolean =
        File(root(ctx), ".complete").exists() &&
            File(dir(ctx), "model.int8.onnx").exists() &&
            File(dir(ctx), "voices.bin").exists() &&
            File(dir(ctx), "espeak-ng-data").isDirectory

    /** Downloads and unpacks the model. Returns null on success, otherwise a message. */
    suspend fun download(ctx: Context): String? = withContext(Dispatchers.IO) {
        val r = root(ctx)
        try {
            release()
            r.deleteRecursively()
            r.mkdirs()
            progress = 0f
            val c = URL(MODEL_URL).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            if (c.responseCode !in 200..299) throw IOException("server returned ${c.responseCode}")
            val total = c.contentLengthLong.coerceAtLeast(1L)
            var last = 0f
            val counting = Counting(c.inputStream) { read ->
                val p = (read.toFloat() / total).coerceIn(0f, 1f)
                if (p - last >= 0.01f) { last = p; progress = p }
            }
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counting, 1 shl 16))).use { tar ->
                var e = tar.nextEntry
                val base = r.canonicalPath
                while (e != null) {
                    val out = File(r, e.name)
                    if (!out.canonicalPath.startsWith(base)) throw IOException("unexpected file in archive")
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { tar.copyTo(it) }
                    }
                    e = tar.nextEntry
                }
            }
            c.disconnect()
            File(r, ".complete").writeText("ok")
            null
        } catch (e: Exception) {
            r.deleteRecursively()
            "The download didn't finish (${e.message}). Check your connection and try again."
        } finally {
            progress = null
        }
    }

    @Synchronized
    private fun engine(ctx: Context): OfflineTts {
        tts?.let { return it }
        val d = dir(ctx).absolutePath
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$d/model.int8.onnx",
                    voices = "$d/voices.bin",
                    tokens = "$d/tokens.txt",
                    dataDir = "$d/espeak-ng-data",
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
        )
        return OfflineTts(config = config).also { tts = it }
    }

    /** Loads the model ahead of time so the first reply isn't slow. */
    suspend fun warm(ctx: Context) = withContext(Dispatchers.Default) {
        if (ready(ctx)) runCatching { engine(ctx) }
    }

    /** Speaks into a WAV file, or returns null so the caller can fall back. */
    suspend fun synthesize(ctx: Context, text: String, sid: Int): File? = withContext(Dispatchers.Default) {
        if (!ready(ctx)) return@withContext null
        try {
            val audio = engine(ctx).generate(text, sid = sid, speed = 0.95f)
            if (audio.samples.isEmpty()) null
            else writeWav(File(ctx.cacheDir, "kokoro_reply.wav"), audio.samples, audio.sampleRate)
        } catch (e: Throwable) {
            null
        }
    }

    @Synchronized
    fun release() {
        runCatching { tts?.release() }
        tts = null
    }

    fun delete(ctx: Context) {
        release()
        root(ctx).deleteRecursively()
    }

    private fun writeWav(f: File, samples: FloatArray, rate: Int): File {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + data.capacity())
        h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
        h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(rate); h.putInt(rate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(data.capacity())
        FileOutputStream(f).use { it.write(h.array()); it.write(data.array()) }
        return f
    }

    private class Counting(inner: InputStream, val onRead: (Long) -> Unit) : FilterInputStream(inner) {
        private var count = 0L
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) { count += 1; onRead(count) }
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) { count += n; onRead(count) }
            return n
        }
    }
}
