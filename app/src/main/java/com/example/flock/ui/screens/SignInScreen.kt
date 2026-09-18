package com.example.flock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandEmerald

/**
 * Landing / sign-in screen. Two clear choices: Google (for cloud + sharing) or offline (local only).
 */
@Composable
fun SignInScreen(
    onSignInGoogle: () -> Unit,
    onOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize().testTag("sign_in_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🐔",
                fontSize = 56.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "FlockIt",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = BrandEmerald
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Broiler flock manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onSignInGoogle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_sign_in_google"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(0.dp))
                Text(
                    "   Sign in with Google",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = onOffline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_offline"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = BrandEmerald)
                Text(
                    "   Use offline (local only)",
                    color = BrandEmerald,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Sign in with Google to keep each farm in your own Google Sheet and share it " +
                        "with your team by email. Offline mode keeps everything on this phone only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
