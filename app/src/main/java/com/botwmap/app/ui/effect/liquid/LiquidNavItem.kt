package com.botwmap.app.ui.effect.liquid

import androidx.compose.ui.graphics.vector.ImageVector

data class LiquidNavItem(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)
