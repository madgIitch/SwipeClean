package com.example.swipeclean

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.madglitch.swipeclean.GalleryViewModel
import com.tuempresa.swipeclean.MediaFilter

private const val TAG_UI = "SwipeClean/UI"
private const val TAG_TUTORIAL = "SwipeClean/Tutorial"
private const val TAG_PERMS = "SwipeClean/Perms"
private const val TAG_INTENT = "SwipeClean/Intent"

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    // Lanzador del tutorial
    private val tutorialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG_TUTORIAL, "Tutorial resultCode=${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG_TUTORIAL, "Tutorial completado → vm.markTutorialCompleted()")
            vm.markTutorialCompleted()
        } else {
            Log.w(TAG_TUTORIAL, "Tutorial cancelado o cerrado sin OK; no se marca completado")
        }
    }

    // Lanzador para diálogos del sistema (trash/delete)
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        Log.d(TAG_INTENT, "Delete/Trash resultCode=${res.resultCode}")
        // if (res.resultCode == RESULT_OK) vm.onTrashCommitted()
    }

    // Lanzador de permisos de galería
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val grantedNow = result.values.any { it }
        Log.d(TAG_PERMS, "RequestMultiplePermissions → $result (grantedNow=$grantedNow)")
        if (grantedNow) {
            // Importante: VM no debe resetear índice; restaura por currentUri/index.
            vm.load()
        }
    }

    // ← NUEVO: Lanzador de permiso de notificaciones
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG_PERMS, "POST_NOTIFICATIONS permission granted: $isGranted")
        if (!isGranted) {
            Log.w(TAG_PERMS, "Notificaciones denegadas - el temporizador Zen no podrá notificar")
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG_UI, "onCreate")

        requestGalleryPermissionsIfNeeded()
        requestNotificationPermissionIfNeeded()  // ← NUEVO

        setContent {
            SwipeCleanTheme {
                SetupSystemBars()

                val tutorialCompleted by vm.tutorialCompleted.collectAsState()
                var launchedTutorial by rememberSaveable { mutableStateOf(false) }

                // Observación para resetear el guard cuando el tutorial quede completado
                LaunchedEffect(tutorialCompleted) {
                    Log.d(TAG_TUTORIAL, "observe → tutorialCompleted=$tutorialCompleted")
                    if (tutorialCompleted && launchedTutorial) {
                        Log.d(TAG_TUTORIAL, "flag=true → reset launchedTutorial guard")
                        launchedTutorial = false
                    }
                }

                // Lanza el tutorial una única vez por sesión mientras no esté completado
                LaunchedEffect(tutorialCompleted, launchedTutorial) {
                    if (!tutorialCompleted && !launchedTutorial) {
                        Log.d(TAG_TUTORIAL, "launch TutorialActivity (first time this session)")
                        launchedTutorial = true
                        tutorialLauncher.launch(Intent(this@MainActivity, TutorialActivity::class.java))
                    } else {
                        Log.d(
                            TAG_TUTORIAL,
                            "skip launch (tutorialCompleted=$tutorialCompleted, launchedTutorial=$launchedTutorial)"
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    color = Color.Transparent
                ) {
                    CardScreen(vm = vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG_UI, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_UI, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG_UI, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG_UI, "onStop → vm.persistNow()")
        vm.persistNow()
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------
    private fun launchIntentSender(sender: IntentSender) {
        Log.d(TAG_INTENT, "launchIntentSender()")
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private fun requestGalleryPermissionsIfNeeded() {
        val perms = requiredGalleryPermissions()
        val alreadyGranted = perms.all { isGranted(it) }
        Log.d(TAG_PERMS, "requestIfNeeded → alreadyGranted=$alreadyGranted, perms=${perms.toList()}")

        if (!alreadyGranted) {
            permissionsLauncher.launch(perms)
        } else {
            Log.d(TAG_PERMS, "ya concedidos, no se pide de nuevo")
        }
    }

    // ← NUEVO: Solicitar permiso de notificaciones
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = isGranted(Manifest.permission.POST_NOTIFICATIONS)
            Log.d(TAG_PERMS, "POST_NOTIFICATIONS permission: $hasPermission")

            if (!hasPermission) {
                Log.d(TAG_PERMS, "Solicitando permiso POST_NOTIFICATIONS")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            Log.d(TAG_PERMS, "POST_NOTIFICATIONS no requerido (API < 33)")
        }
    }

    private fun requiredGalleryPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SetupSystemBars() {
    val sys = rememberSystemUiController()
    val darkIcons = !isSystemInDarkTheme()
    SideEffect {
        sys.setStatusBarColor(Color.Transparent, darkIcons = darkIcons)
        sys.setNavigationBarColor(Color.Transparent, darkIcons = darkIcons)
    }
}