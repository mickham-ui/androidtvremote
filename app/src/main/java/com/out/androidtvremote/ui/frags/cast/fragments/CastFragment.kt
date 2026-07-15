package com.out.androidtvremote.ui.frags.cast.fragments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.FragmentCastingBinding
import com.out.androidtvremote.interfaces.BottomNavigationListener
import com.out.androidtvremote.ui.activities.MainActivity


class CastFragment : Fragment()  {

    private var TAG = CastFragment::class.java.simpleName
    private var _binding: FragmentCastingBinding? = null
    private var bottomNavigationListener: BottomNavigationListener? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    companion object {
        private const val REQUEST_PERMISSION_CODE = 123
        private const val PERMISSION_DENIED_TITLE = "Permission Required"
        private const val PERMISSION_DENIED_MESSAGE =
            "To access media files, you need to grant storage permissions. Without this permission, you won't be able to view images, videos, or audio files."

    }

    // Permission request callback
    private var permissionRequestCallback: (() -> Unit)? = null
    private val binding get() = _binding!!
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Ensure the activity implements the interface

    }

    override fun onDetach() {
        super.onDetach()

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        _binding = FragmentCastingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listeners for buttons to navigate to corresponding fragments
        binding.cardScreenMirroring.setOnClickListener {
            try {
                startActivity(Intent("android.settings.WIFI_DISPLAY_SETTINGS"))
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                try {
                    startActivity(Intent("com.samsung.wfd.LAUNCH_WFD_PICKER_DLG"))
                } catch (e2: Exception) {
                    try {
                        startActivity(Intent("android.settings.CAST_SETTINGS"))
                    } catch (e3: Exception) {
                        Toast.makeText(
                            context ?: requireContext(),
                            "Device not supported",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        binding.cardAudio.setOnClickListener {
            requestStoragePermissionAndNavigate {
                MainActivity.castFragsNumbers.value = "3"
                navigateToAudioFragment()
            }

        }

        binding.cardVideo.setOnClickListener {
            requestStoragePermissionAndNavigate {
                MainActivity.castFragsNumbers?.value = "2"
                navigateToVideoFragment()
            }
        }
        binding.cardPhoto.setOnClickListener {
            requestStoragePermissionAndNavigate {
                MainActivity.castFragsNumbers?.value = "1"
                navigateToImageFragment()
            }
        }

    }

    private fun navigateToVideoFragment() {
        findNavController().navigate(R.id.action_castingFragment_to_videoFragment)
    }

    private fun navigateToAudioFragment() {
        findNavController().navigate(R.id.action_castingFragment_to_audioFragment)
    }

    private fun navigateToImageFragment() {
        findNavController().navigate(R.id.action_castingFragment_to_photoFragment)
    }

    private fun NavController.navigateWithOptions(@IdRes destination: Int) {
        // Only way to use custom animations with the bottom navigation bar
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.slide_in_bottom)
            .setExitAnim(R.anim.slide_out_bottom)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.fade_out)
//            .setPopUpTo(R.id.mobile_navigation, true)
            .build()

        navigate(destination, null, options)
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun requestStoragePermissionAndNavigate(onPermissionGranted: () -> Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            permissionsToRequest.addAll(
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
            permissionsToRequest.addAll(
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else { // Android 12 and below
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Filter out permissions that are already granted
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        // Check if Android 14+ partial access is granted
        val hasPartialAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

        // If no missing permissions, or if we have partial access on Android 14+, proceed
        if (missingPermissions.isEmpty() || hasPartialAccess) {
            onPermissionGranted.invoke()
        } else {
            // Need to request permissions
            permissionRequestCallback = {
                // Double check after request
                val stillMissing = permissionsToRequest.filter {
                    ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
                }
                val nowHasPartial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

                if (stillMissing.isEmpty() || nowHasPartial) {
                    onPermissionGranted.invoke()
                } else {
                    showPermissionDeniedDialog()
                }
            }
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // Check if ANY required permission was granted, or if partial access was given
            val anyGranted = result.any { it.value }
            val partialAccessGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    result[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true

            if (anyGranted || partialAccessGranted) {
                // If the user granted at least something, let's proceed and double check in the callback
                permissionRequestCallback?.invoke()
            } else {
                // Everything was denied
                showPermissionDeniedDialog()
            }
            permissionRequestCallback = null
        }


    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(PERMISSION_DENIED_TITLE)
            .setMessage(PERMISSION_DENIED_MESSAGE)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.clear() // Clear existing menu items
    }


}
