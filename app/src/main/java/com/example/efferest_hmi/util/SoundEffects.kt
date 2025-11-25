package com.example.efferest_hmi.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.efferest_hmi.R
import java.util.concurrent.atomic.AtomicBoolean

object SoundEffects {

    private const val TAG = "EFFEREST_HVAC"

    private var soundPool: SoundPool? = null
    private var beepId: Int = 0
    private val initialized = AtomicBoolean(false)
    private val loaded = AtomicBoolean(false)

    fun ensureInit(context: Context) {
        if (initialized.get()) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == beepId) {
                loaded.set(true)
                Log.d(TAG, "Sound loaded: beep_short (id=$beepId)")
            } else {
                Log.w(TAG, "Failed to load sound (status=$status, sampleId=$sampleId)")
            }
        }

        beepId = soundPool!!.load(context, R.raw.beep_short, 1)
        initialized.set(true)
        Log.d(TAG, "Initiated SoundPool, loading beep_short...")
    }

    fun playBeep() {
        if (!initialized.get()) {
            Log.w(TAG, "playBeep() called before initialization")
            return
        }
        if (!loaded.get()) {
            Log.d(TAG, "playBeep() requested but not yet loaded")
            return
        }
        val result = soundPool?.play(beepId, 1f, 1f, 0, 0, 1f)
        if (result == 0) {
            Log.w(TAG, "SoundPool.play returned 0 (playback failed)")
        } else {
            Log.d(TAG, "Played beep_short (streamId=$result)")
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        initialized.set(false)
        loaded.set(false)
        Log.d(TAG, "SoundPool released")
    }
}