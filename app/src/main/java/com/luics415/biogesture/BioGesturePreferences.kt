package com.luics415.biogesture

import android.content.Context
import androidx.core.content.edit

class BioGesturePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mirrorMovement: Boolean
        get() = preferences.getBoolean(KEY_MIRROR, true)
        set(value) = preferences.edit { putBoolean(KEY_MIRROR, value) }

    var performanceProfile: PerformanceProfile
        get() = PerformanceProfile.fromStored(preferences.getString(KEY_PROFILE, null))
        set(value) = preferences.edit { putString(KEY_PROFILE, value.name) }

    var showLandmarks: Boolean
        get() = preferences.getBoolean(KEY_SHOW_LANDMARKS, false)
        set(value) = preferences.edit { putBoolean(KEY_SHOW_LANDMARKS, value) }

    fun calibrationBounds(landscape: Boolean): InputCalibrationBounds {
        val suffix = if (landscape) LANDSCAPE_SUFFIX else PORTRAIT_SUFFIX
        val legacyHorizontal = preferences.getFloat(
            KEY_HORIZONTAL_MARGIN,
            ScreenCoordinateMapper.DEFAULT_INPUT_MARGIN,
        )
        val legacyVertical = preferences.getFloat(
            KEY_VERTICAL_MARGIN,
            ScreenCoordinateMapper.DEFAULT_INPUT_MARGIN,
        )
        return InputCalibrationBounds(
            minX = preferences.getFloat(
                KEY_MIN_X + suffix,
                preferences.getFloat(KEY_MIN_X, legacyHorizontal),
            ),
            maxX = preferences.getFloat(
                KEY_MAX_X + suffix,
                preferences.getFloat(KEY_MAX_X, 1f - legacyHorizontal),
            ),
            minY = preferences.getFloat(
                KEY_MIN_Y + suffix,
                preferences.getFloat(KEY_MIN_Y, legacyVertical),
            ),
            maxY = preferences.getFloat(
                KEY_MAX_Y + suffix,
                preferences.getFloat(KEY_MAX_Y, 1f - legacyVertical),
            ),
        ).sanitized()
    }

    fun resetCalibration() {
        preferences.edit {
            remove(KEY_HORIZONTAL_MARGIN)
            remove(KEY_VERTICAL_MARGIN)
            remove(KEY_MIN_X)
            remove(KEY_MAX_X)
            remove(KEY_MIN_Y)
            remove(KEY_MAX_Y)
            remove(KEY_MIN_X + PORTRAIT_SUFFIX)
            remove(KEY_MAX_X + PORTRAIT_SUFFIX)
            remove(KEY_MIN_Y + PORTRAIT_SUFFIX)
            remove(KEY_MAX_Y + PORTRAIT_SUFFIX)
            remove(KEY_MIN_X + LANDSCAPE_SUFFIX)
            remove(KEY_MAX_X + LANDSCAPE_SUFFIX)
            remove(KEY_MIN_Y + LANDSCAPE_SUFFIX)
            remove(KEY_MAX_Y + LANDSCAPE_SUFFIX)
        }
    }

    fun saveCalibration(bounds: InputCalibrationBounds, landscape: Boolean) {
        val safe = bounds.sanitized()
        val suffix = if (landscape) LANDSCAPE_SUFFIX else PORTRAIT_SUFFIX
        preferences.edit {
            putFloat(KEY_MIN_X + suffix, safe.minX)
            putFloat(KEY_MAX_X + suffix, safe.maxX)
            putFloat(KEY_MIN_Y + suffix, safe.minY)
            putFloat(KEY_MAX_Y + suffix, safe.maxY)
            remove(KEY_HORIZONTAL_MARGIN)
            remove(KEY_VERTICAL_MARGIN)
        }
    }

    fun requestCalibration() {
        preferences.edit { putBoolean(KEY_CALIBRATION_REQUESTED, true) }
    }

    fun consumeCalibrationRequest(): Boolean {
        if (!preferences.getBoolean(KEY_CALIBRATION_REQUESTED, false)) return false
        preferences.edit { putBoolean(KEY_CALIBRATION_REQUESTED, false) }
        return true
    }

    companion object {
        private const val PREFS_NAME = "biogesture_preferences"
        private const val KEY_MIRROR = "mirror_movement"
        private const val KEY_PROFILE = "performance_profile"
        private const val KEY_SHOW_LANDMARKS = "show_landmarks"
        private const val KEY_HORIZONTAL_MARGIN = "horizontal_margin"
        private const val KEY_VERTICAL_MARGIN = "vertical_margin"
        private const val KEY_MIN_X = "calibration_min_x"
        private const val KEY_MAX_X = "calibration_max_x"
        private const val KEY_MIN_Y = "calibration_min_y"
        private const val KEY_MAX_Y = "calibration_max_y"
        private const val PORTRAIT_SUFFIX = "_portrait"
        private const val LANDSCAPE_SUFFIX = "_landscape"
        private const val KEY_CALIBRATION_REQUESTED = "calibration_requested"
    }
}

enum class PerformanceProfile(val targetFps: Int) {
    SAVER(12),
    BALANCED(20),
    PRECISION(30);

    companion object {
        fun fromStored(value: String?): PerformanceProfile =
            entries.firstOrNull { it.name == value } ?: BALANCED
    }
}
