package com.esgis.chatapp.data

import android.media.MediaPlayer

/** Lecture d'un message audio en streaming depuis son URL publique. */
class AudioPlayer {

    private var player: MediaPlayer? = null

    fun play(url: String) {
        stop()
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { this.stop() }
            mp.setOnErrorListener { _, _, _ -> this.stop(); true }
            mp.prepareAsync()
            player = mp
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
    }
}
