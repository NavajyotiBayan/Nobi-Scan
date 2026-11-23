package com.nobi.scan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.nobi.scan.data.ScanPreferencesStore
import com.nobi.scan.ui.reader.PdfReaderScreen
import com.nobi.scan.ui.theme.NobiScanTheme

class PdfReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW && it.scheme == "content" }
        if (source == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        val appearance = ScanPreferencesStore(this).read().appearance
        setContent {
            val dark = appearance.isDark(isSystemInDarkTheme())
            NobiScanTheme(dark) {
                PdfReaderScreen(source = source, onClose = ::finish)
            }
        }
    }

    companion object {
        internal fun openIntent(context: Context, uri: Uri): Intent =
            Intent(context, PdfReaderActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
