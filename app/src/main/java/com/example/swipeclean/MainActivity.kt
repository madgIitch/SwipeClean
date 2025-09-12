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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.madglitch.swipeclean.GalleryViewModel
import com.tuempresa.swipeclean.MediaFilter

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    // Diálogos del sistema (trash/delete)
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* opcional: comprobar resultCode */ }

    // Permisos
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        vm.load(MediaFilter.ALL)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // bájalo si tu minSdk lo requiere
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestGalleryPermissions()

        setContent {
            SwipeCleanTheme {
                // === System bars transparentes y con iconos adecuados ===
                SetupSystemBars()

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // CardScreen ya gestiona abrir ReviewActivity y procesar el resultado
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

// ─────────────────────────────────────────────────────────────────────────────
// System bars helper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SetupSystemBars() {
    val sys = rememberSystemUiController()
    val darkIcons = !isSystemInDarkTheme() // usa iconos oscuros si no estamos en dark mode
    SideEffect {
        sys.setStatusBarColor(Color.Transparent, darkIcons = darkIcons)
        sys.setNavigationBarColor(Color.Transparent, darkIcons = darkIcons)
    }
}
