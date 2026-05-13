package com.ledger.app.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticManager(context: Context) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Sharp, metallic double-tap for a Sale.
     */
    fun playSaleClink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 10, 40, 10), intArrayOf(0, 255, 0, 180), -1)
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(20)
        }
    }

    /**
     * Soft, heavy thud for an Expense.
     */
    fun playExpenseThud() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(50, 120)
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(50)
        }
    }

    /**
     * Quick tick for general interactions.
     */
    fun playTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(10)
        }
    }
}
