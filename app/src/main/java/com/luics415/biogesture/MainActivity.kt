package com.luics415.biogesture

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox

class MainActivity : AppCompatActivity() {
    private lateinit var preferences: BioGesturePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = BioGesturePreferences(this)

        configurePreferences()
        configureActions()
        showVersion()
    }

    override fun onResume() {
        super.onResume()
        updateReadinessStatus()
    }

    @Deprecated("Kept for compatibility with the minimum supported Android API.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_REQUEST) return
        updateReadinessStatus()
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (granted) R.string.camera_permission_ready else R.string.camera_permission_required,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun configureActions() {
        findViewById<MaterialButton>(R.id.btn_enable_service).setOnClickListener {
            if (!hasCameraPermission()) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        findViewById<MaterialButton>(R.id.btn_reset_calibration).setOnClickListener {
            preferences.requestCalibration()
            Toast.makeText(this, R.string.calibration_requested, Toast.LENGTH_LONG).show()
        }

        findViewById<MaterialButton>(R.id.btn_manual).setOnClickListener {
            openWebPage(MANUAL_URL)
        }
        findViewById<MaterialButton>(R.id.btn_github).setOnClickListener {
            openWebPage(REPOSITORY_URL)
        }
    }

    private fun configurePreferences() {
        findViewById<MaterialCheckBox>(R.id.switch_mirror).apply {
            isChecked = preferences.mirrorMovement
            setOnCheckedChangeListener { _, checked -> preferences.mirrorMovement = checked }
        }
        findViewById<MaterialCheckBox>(R.id.switch_landmarks).apply {
            isChecked = preferences.showLandmarks
            setOnCheckedChangeListener { _, checked -> preferences.showLandmarks = checked }
        }

        val group = findViewById<MaterialButtonToggleGroup>(R.id.performance_group)
        group.check(buttonFor(preferences.performanceProfile))
        updateProfileDescription(preferences.performanceProfile)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val profile = profileFor(checkedId)
            preferences.performanceProfile = profile
            updateProfileDescription(profile)
        }
    }

    private fun updateReadinessStatus() {
        val serviceEnabled = isBioGestureServiceEnabled()
        val cameraReady = hasCameraPermission()

        findViewById<TextView>(R.id.service_status).apply {
            setText(if (serviceEnabled) R.string.service_enabled else R.string.service_disabled)
            setTextColor(getColor(if (serviceEnabled) R.color.status_enabled else R.color.status_disabled))
        }
        findViewById<android.view.View>(R.id.service_status_indicator).backgroundTintList =
            ColorStateList.valueOf(
                getColor(if (serviceEnabled) R.color.status_enabled else R.color.status_disabled),
            )
        findViewById<TextView>(R.id.camera_status).apply {
            setText(if (cameraReady) R.string.camera_ready else R.string.camera_pending)
            setTextColor(getColor(if (cameraReady) R.color.status_enabled else R.color.text_secondary))
        }
        findViewById<MaterialButton>(R.id.btn_enable_service).setText(
            when {
                !cameraReady -> R.string.grant_camera
                serviceEnabled -> R.string.manage_accessibility
                else -> R.string.enable_accessibility
            },
        )
    }

    private fun isBioGestureServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == packageName &&
                    serviceInfo.name == BioGestureService::class.java.name
            }
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun updateProfileDescription(profile: PerformanceProfile) {
        findViewById<TextView>(R.id.profile_description).setText(
            when (profile) {
                PerformanceProfile.SAVER -> R.string.profile_saver_description
                PerformanceProfile.BALANCED -> R.string.profile_balanced_description
                PerformanceProfile.PRECISION -> R.string.profile_precision_description
            },
        )
    }

    private fun buttonFor(profile: PerformanceProfile): Int = when (profile) {
        PerformanceProfile.SAVER -> R.id.btn_profile_saver
        PerformanceProfile.BALANCED -> R.id.btn_profile_balanced
        PerformanceProfile.PRECISION -> R.id.btn_profile_precision
    }

    private fun profileFor(buttonId: Int): PerformanceProfile = when (buttonId) {
        R.id.btn_profile_saver -> PerformanceProfile.SAVER
        R.id.btn_profile_precision -> PerformanceProfile.PRECISION
        else -> PerformanceProfile.BALANCED
    }

    @Suppress("DEPRECATION")
    private fun showVersion() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.app_version).text = getString(R.string.version_format, versionName)
    }

    private fun openWebPage(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.browser_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val REPOSITORY_URL =
            "https://github.com/Luics415/Bio-Gesture-Control-Android"
        private const val MANUAL_URL =
            "$REPOSITORY_URL/blob/main/docs/MANUAL_DE_USUARIO.md"
    }
}
