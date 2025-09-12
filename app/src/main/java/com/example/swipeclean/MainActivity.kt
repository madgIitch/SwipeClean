package com.example.swipeclean

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.madglitch.swipeclean.GalleryViewModel
import com.tuempresa.swipeclean.MediaFilter

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    // Lanzador para diálogos del sistema (trash/delete)
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        // Opcional: comprobar resultCode si quieres diferenciar ACEPTAR / CANCELAR
        // if (it.resultCode == RESULT_OK) { vm.onTrashCommitted() }
    }

    // Lanzador de permisos
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // ¿Se ha concedido al menos uno ahora?
        val grantedNow = result.values.any { it }
        if (grantedNow) {
            // Importante: vm.load() NO debe resetear el índice a 0; debe restaurar por currentUri/index
            vm.load(MediaFilter.ALL)
        }
        // Si ya estaban concedidos antes, no hacemos nada: el init del VM ya restauró.
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pide permisos solo si faltan; si ya estaban, no toques nada.
        requestGalleryPermissionsIfNeeded()

        setContent {
            SwipeCleanTheme {
                SetupSystemBars()

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Si tu CardScreen necesita lanzar el IntentSender del sistema,
                    // puedes exponer un callback y pasar ::launchIntentSender.
                    // Ahora mismo solo pasamos el ViewModel.
                    CardScreen(vm = vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Asegura que el último currentUri/index quedan guardados incluso si el sistema mata la app
        vm.persistNow()
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------
    private fun launchIntentSender(sender: IntentSender) {
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private fun requestGalleryPermissionsIfNeeded() {
        val perms = requiredGalleryPermissions()
        val alreadyGranted = perms.all { isGranted(it) }

        if (!alreadyGranted) {
            permissionsLauncher.launch(perms)
        }
        // Si ya estaban concedidos, no dispares vm.load(): el ViewModel restaurará solo en su init.
    }

    private fun requiredGalleryPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

// ─────────────────────────────────────────────────────────────────────────────
// System bars helper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SetupSystemBars() {
    val sys = rememberSystemUiController()
    val darkIcons = !isSystemInDarkTheme()
    SideEffect {
        sys.setStatusBarColor(Color.Transparent, darkIcons = darkIcons)
        sys.setNavigationBarColor(Color.Transparent, darkIcons = darkIcons)
    }
}
