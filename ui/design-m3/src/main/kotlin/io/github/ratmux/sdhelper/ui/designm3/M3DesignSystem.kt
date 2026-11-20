package io.github.ratmux.sdhelper.ui.designm3

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ratmux.sdhelper.ui.designapi.DesignSystem

/** Material 3 implementation of the design contract. Dynamic color on Android 12+, static palette below. */
object M3DesignSystem : DesignSystem {
    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        val dark = isSystemInDarkTheme()
        val colors =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }

                dark -> {
                    darkColorScheme()
                }

                else -> {
                    lightColorScheme()
                }
            }
        MaterialTheme(colorScheme = colors, content = content)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Scaffold(
        title: String,
        modifier: Modifier,
        content: @Composable (PaddingValues) -> Unit,
    ) {
        Scaffold(
            modifier = modifier,
            topBar = { CenterAlignedTopAppBar(title = { Text(title) }) },
            content = content,
        )
    }

    @Composable
    override fun Card(
        title: String,
        subtitle: String?,
        onClick: () -> Unit,
        modifier: Modifier,
    ) {
        Card(onClick = onClick, modifier = modifier) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
