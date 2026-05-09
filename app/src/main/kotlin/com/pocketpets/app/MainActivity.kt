package com.pocketpets.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pocketpets.app.ui.nav.AppNav
import com.pocketpets.app.ui.theme.PocketPetsTheme

val LocalDeepLinkPetId = compositionLocalOf<Long?> { null }

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* result ignored — UI fallback already shown when denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotifPermissionIfNeeded()
        val deepLinkPetId = intent?.extras?.getLong("petId", -1L)?.takeIf { it > 0 }
        setContent {
            PocketPetsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalDeepLinkPetId provides deepLinkPetId) {
                        AppNav()
                    }
                }
            }
        }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
