package com.tuempresa.swipeclean

import android.Manifest
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.swipeclean.CardScreen
import com.example.swipeclean.GalleryViewModel

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    // Lanzador para los diálogos del sistema (trash/delete)
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* puedes comprobar resultCode si quieres */ }

    // Permisos de runtime
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // En cuanto conteste, intentamos cargar
        vm.load(MediaFilter.ALL)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestGalleryPermissions()

        setContent {
            MaterialTheme {
                CardScreen(
                    vm = vm,
                    onNeedUserConfirm = { sender -> launchIntentSender(sender) }
                )
            }
        }
    }

    private fun launchIntentSender(sender: IntentSender) {
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private fun requestGalleryPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionsLauncher.launch(perms)
    }
}
