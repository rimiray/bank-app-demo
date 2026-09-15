package com.bankapp.zbk.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.ui.graphics.vector.ImageVector

enum class ZbkDestination(
    val label: String,
    val icon: ImageVector,
) {
    Cards("My Cards", Icons.Outlined.AccountBalanceWallet),
    Credit("Credit", Icons.Outlined.Calculate),
}
