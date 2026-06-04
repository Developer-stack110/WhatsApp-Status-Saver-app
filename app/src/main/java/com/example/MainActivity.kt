package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.service.StatusStorageManager
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.StatusSaverViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: StatusSaverViewModel

    // Activity Result Launcher for standard WhatsApp folder select (Android 11+ SAF)
    private val openWATreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                // Take persistable permissions so we can read it on next starts
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                StatusStorageManager.savePersistedUriPermission(this, uri, false)
                viewModel.grantWhatsAppFolder(uri.toString(), false)
                Toast.makeText(this, "WhatsApp folder permission granted!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to persist folder rights: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Activity Result Launcher for WhatsApp Business folder select (Android 11+ SAF)
    private val openWABTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                StatusStorageManager.savePersistedUriPermission(this, uri, true)
                viewModel.grantWhatsAppFolder(uri.toString(), true)
                Toast.makeText(this, "WhatsApp Business folder permission granted!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to persist folder rights: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Activity Result Launcher for standard Storage permission (Android 10 and below)
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (readGranted) {
            viewModel.updateStoragePermission(true)
            Toast.makeText(this, "Storage permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.updateStoragePermission(false)
            Toast.makeText(this, "Storage permission is required for Android 10 and below.", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup edge-to-edge rendering
        enableEdgeToEdge()

        // Setup ViewModel
        viewModel = ViewModelProvider(this)[StatusSaverViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val currentRoute by viewModel.currentRoute.collectAsState()

                    // Handle fade transitions between primary sequence views
                    AnimatedContent(
                        targetState = currentRoute,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "MainLayoutSequencer"
                    ) { targetLocalRoute ->
                        when (targetLocalRoute) {
                            "splash" -> {
                                SplashScreen(
                                    viewModel = viewModel,
                                    onNavigate = { viewModel.completeSplash() }
                                )
                            }
                            "welcome" -> {
                                WelcomeScreen(
                                    viewModel = viewModel,
                                    onNext = { viewModel.setRoute("language") },
                                    onSkip = { viewModel.completeOnboarding() }
                                )
                            }
                            "language" -> {
                                LanguageSelectionScreen(
                                    viewModel = viewModel,
                                    onContinue = { viewModel.setRoute("onboarding") }
                                )
                            }
                            "onboarding" -> {
                                OnboardingScreen(
                                    viewModel = viewModel,
                                    onGetStarted = { viewModel.completeOnboarding() }
                                )
                            }
                            "home" -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onRequestSAF = { isBusiness -> launchSAF(isBusiness) },
                                    onRequestStoragePermission = { requestStoragePermission() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= 29) {
            requestStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun launchSAF(isBusiness: Boolean) {
        val treeUri = StatusStorageManager.getWhatsAppTreeUri(isBusiness)
        try {
            val launcher = if (isBusiness) openWABTreeLauncher else openWATreeLauncher
            launcher.launch(treeUri)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to launch folder selector without initial starting state
            val launcher = if (isBusiness) openWABTreeLauncher else openWATreeLauncher
            launcher.launch(null)
        }
    }
}
