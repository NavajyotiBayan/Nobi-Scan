package com.nobi.scan

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nobi.scan.data.AppAppearance
import com.nobi.scan.data.ScanPreferencesStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nobi.scan.ui.NobiRootScreen
import com.nobi.scan.ui.theme.LocalNobiPalette
import com.nobi.scan.ui.theme.NobiScanTheme
import com.nobi.scan.update.rememberUpdateControls

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = ScanPreferencesStore(this)
        setContent {
            var preferences by remember { mutableStateOf(store.read()) }
            val updates = if (BuildConfig.GITHUB_UPDATES) rememberUpdateControls() else null
            val isDark = preferences.appearance.isDark(isSystemInDarkTheme())
            NobiScanTheme(isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalNobiPalette.current.background
                ) {
                    NobiRootScreen(
                        preferences = preferences,
                        updates = updates,
                        onPreferencesChanged = { preferences = it; store.write(it) },
                        onToggleTheme = {
                            preferences = preferences.copy(
                                appearance = if (isDark) AppAppearance.LIGHT else AppAppearance.DARK
                            )
                            store.write(preferences)
                        }
                    )
                }
            }
        }
    }
}
