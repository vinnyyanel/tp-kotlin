package com.esgis.chatapp.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Enregistreur de messages vocaux (AAC / m4a). */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean = try {
        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        true
    } catch (e: Exception) {
        recorder = null
        false
    }

    /** Arrête l'enregistrement et renvoie les octets (ou null en cas d'erreur). */
    fun stop(): ByteArray? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            outputFile?.readBytes()
        } catch (e: Exception) {
            null
        } finally {
            recorder = null
        }
    }
}
