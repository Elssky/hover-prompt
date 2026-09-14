package com.example.hoverprompt

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Persists the user's last teleprompter settings for both the activity and the
 * independent floating-window service.
 */
internal object PromptSettingsStore {
    private const val PREFS_NAME = "hover_prompt_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_SPEED = "speed"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_LINE_SPACING = "line_spacing"
    private const val KEY_TEXT_COLOR = "text_color"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_LOOP = "loop"
    private const val KEY_COUNTDOWN = "countdown"

    fun load(context: Context): PromptSettings {
        val defaults = PromptSettings()
        val prefs = preferences(context)
        val mode = runCatching {
            prefs.getString(KEY_MODE, defaults.mode.name)?.let(PromptMode::valueOf)
        }.getOrNull() ?: defaults.mode

        return defaults.copy(
            mode = mode,
            speed = prefs.float(KEY_SPEED, defaults.speed).coerceIn(8f, 60f),
            fontSize = prefs.float(KEY_FONT_SIZE, defaults.fontSize).coerceIn(16f, 36f),
            lineSpacing = prefs.float(KEY_LINE_SPACING, defaults.lineSpacing).coerceIn(1f, 2f),
            textColor = Color(prefs.int(KEY_TEXT_COLOR, defaults.textColor.toArgb())),
            backgroundColor = Color(prefs.int(KEY_BACKGROUND_COLOR, defaults.backgroundColor.toArgb())),
            opacity = prefs.float(KEY_OPACITY, defaults.opacity).coerceIn(.35f, 1f),
            loop = prefs.getBoolean(KEY_LOOP, defaults.loop),
            countdown = prefs.getBoolean(KEY_COUNTDOWN, defaults.countdown)
        )
    }

    fun save(context: Context, settings: PromptSettings) {
        preferences(context).edit()
            .putString(KEY_MODE, settings.mode.name)
            .putFloat(KEY_SPEED, settings.speed)
            .putFloat(KEY_FONT_SIZE, settings.fontSize)
            .putFloat(KEY_LINE_SPACING, settings.lineSpacing)
            .putInt(KEY_TEXT_COLOR, settings.textColor.toArgb())
            .putInt(KEY_BACKGROUND_COLOR, settings.backgroundColor.toArgb())
            .putFloat(KEY_OPACITY, settings.opacity)
            .putBoolean(KEY_LOOP, settings.loop)
            .putBoolean(KEY_COUNTDOWN, settings.countdown)
            .apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun SharedPreferences.float(key: String, fallback: Float): Float =
        runCatching { getFloat(key, fallback) }.getOrDefault(fallback)

    private fun SharedPreferences.int(key: String, fallback: Int): Int =
        runCatching { getInt(key, fallback) }.getOrDefault(fallback)
}
