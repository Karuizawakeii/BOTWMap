package com.botwmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.core.theme.BotwMapTheme
import com.botwmap.app.data.repository.MapRepository
import com.botwmap.app.ui.navigation.AppNavHost
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 显式透明：默认 auto 样式可能给导航栏设不透明底色（底部大白条）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val repository: MapRepository = koinInject()
            val themeMode by repository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val dynamicColor by repository.dynamicColor.collectAsStateWithLifecycle(initialValue = false)

            val systemDark = LocalConfiguration.current.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            BotwMapTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
