package com.nobi.scan.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.DocumentLibrary
import com.nobi.scan.data.ScanPreferences
import com.nobi.scan.update.UpdateControls
import com.nobi.scan.update.UpdateDialog
import com.nobi.scan.ui.camera.CameraScreen
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.LiquidBottomDock
import com.nobi.scan.ui.components.NavigationTab
import com.nobi.scan.ui.home.HomeScreen
import com.nobi.scan.ui.home.HomeScanButton
import com.nobi.scan.ui.settings.SettingsScreen
import com.nobi.scan.ui.tools.ToolsScreen
import com.nobi.scan.ui.tools.MergePdfScreen
import com.nobi.scan.ui.tools.CompressPdfScreen
import com.nobi.scan.ui.tools.OrganizePdfScreen
import com.nobi.scan.ui.theme.SystemBarContrast
import com.nobi.scan.ui.theme.LocalNobiPalette
import java.io.File
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost

private enum class PdfTool { MERGE, ORGANIZE, COMPRESS }

@Composable
internal fun NobiRootScreen(
    preferences: ScanPreferences,
    updates: UpdateControls?,
    onPreferencesChanged: (ScanPreferences) -> Unit,
    onToggleTheme: () -> Unit
) {
    val isDark = LocalNobiPalette.current.isDark
    val context = LocalContext.current
    val library = remember(context) { DocumentLibrary(context.applicationContext) }
    var pdfTool by rememberSaveable { mutableStateOf<PdfTool?>(null) }
    var currentTab by remember { mutableStateOf(if (pdfTool != null) NavigationTab.TOOLS else NavigationTab.HOME) }
    val activeTool = pdfTool.takeIf { currentTab == NavigationTab.TOOLS }

    fun finishTool() {
        pdfTool = null
        currentTab = NavigationTab.HOME
    }

    if (currentTab != NavigationTab.SCAN && activeTool == null) {
        updates?.let { UpdateDialog(it) }
    }

    var editTarget by remember { mutableStateOf<File?>(null) }
    val screenState = rememberSaveableStateHolder()
    key(isDark) {
        GlasmioTheme(isDark = isDark) {
            GlasmioBackdropHost(
                modifier = Modifier.fillMaxSize(),
                sampledContent = {
                    if (currentTab == NavigationTab.SCAN || activeTool != null) {
                        AuraBackgroundBox {}
                    } else {
                        Crossfade(targetState = currentTab, label = "Navigation tab") { tab ->
                            when (tab) {
                                NavigationTab.HOME -> screenState.SaveableStateProvider("home") {
                                    HomeScreen(
                                        library = library,
                                        onToggleTheme = onToggleTheme,
                                        onEditPdf = { file ->
                                            editTarget = file
                                            currentTab = NavigationTab.SCAN
                                        }
                                    )
                                }
                                NavigationTab.TOOLS -> ToolsScreen(
                                    onMergePdfs = { pdfTool = PdfTool.MERGE },
                                    onOrganizePdf = { pdfTool = PdfTool.ORGANIZE },
                                    onCompressPdf = { pdfTool = PdfTool.COMPRESS }
                                )
                                NavigationTab.SETTINGS -> SettingsScreen(preferences, onPreferencesChanged, updates)
                                NavigationTab.SCAN -> Unit
                            }
                        }
                    }
                },
                overlayContent = {
                    if (currentTab == NavigationTab.SCAN) {
                        CameraScreen(library = library, preferences = preferences, initialPdf = editTarget, onClose = {
                            editTarget = null
                            currentTab = NavigationTab.HOME
                        })
                    } else if (activeTool != null) {
                        SystemBarContrast(lightBackground = !isDark)
                        when (activeTool) {
                            PdfTool.MERGE -> MergePdfScreen(
                                library = library,
                                onClose = { pdfTool = null },
                                onSaved = ::finishTool
                            )
                            PdfTool.ORGANIZE -> OrganizePdfScreen(
                                library = library,
                                onClose = { pdfTool = null },
                                onSaved = ::finishTool
                            )
                            PdfTool.COMPRESS -> CompressPdfScreen(
                                library = library,
                                onClose = { pdfTool = null },
                                onSaved = ::finishTool
                            )
                        }
                    } else {
                        SystemBarContrast(lightBackground = !isDark)
                        if (currentTab == NavigationTab.HOME) {
                            HomeScanButton(
                                onOpenScan = { currentTab = NavigationTab.SCAN },
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .navigationBarsPadding().padding(bottom = 82.dp)
                            )
                        }
                        LiquidBottomDock(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            )
        }
    }
}
