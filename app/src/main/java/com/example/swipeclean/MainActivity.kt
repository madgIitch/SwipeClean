package com.example.swipeclean

import android.Manifest
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import com.madglitch.swipeclean.GalleryViewModel
import com.tuempresa.swipeclean.MediaFilter

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* opcional: comprobar resultCode */ }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        vm.load(MediaFilter.ALL)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestGalleryPermissions()

        setContent {
            // Usa tu tema oscuro con background gris-negro (#121212 recomendado)
            SwipeCleanTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // CardScreen internamente puede tener su propio Scaffold;
                    // si no, asegúrate de que también use containerColor = background
                    CardScreen(vm = vm)
                }
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
