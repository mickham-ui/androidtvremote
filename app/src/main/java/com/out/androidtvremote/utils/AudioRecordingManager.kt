package com.out.androidtvremote.utils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AudioRecordingManager(private val fragment: Fragment) {
    private val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

    private var permissionCallback: (() -> Unit)? = null

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionCallback?.invoke()
        } else {
            showPermissionDeniedDialog()
        }
    }

    fun checkAndRequestPermission(onPermissionGranted: () -> Unit) {
        permissionCallback = onPermissionGranted

        when {
            ContextCompat.checkSelfPermission(
                fragment.requireContext(),
                RECORD_AUDIO_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionGranted()
            }
            fragment.shouldShowRequestPermissionRationale(RECORD_AUDIO_PERMISSION) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                permissionLauncher.launch(RECORD_AUDIO_PERMISSION)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Permission Required")
            .setMessage("Audio recording permission is required to record voice notes.")
            .setPositiveButton("Grant") { _, _ ->
                permissionLauncher.launch(RECORD_AUDIO_PERMISSION)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Permission Denied")
            .setMessage("Audio recording permission is required. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", fragment.requireActivity().packageName, null)
            fragment.startActivity(this)
        }
    }
}