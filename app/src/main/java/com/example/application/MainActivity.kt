package com.example.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.application.ui.theme.ApplicationTheme

/**
 * MainActivity
 *
 * Single-activity entry point. It:
 * - Reads an optional "route" from the launch Intent (for deep-links/notifications).
 * - Boots the Compose UI and passes the initial route into the app scaffold.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolve the starting route (defaults to Home if not provided).
        // Notifications (e.g., reminders) can set this extra to deep-link into a screen.
        val initialRoute = intent?.getStringExtra("route") ?: Destination.HOME.route

        setContent {
            // App-wide theme + main scaffold (top bar, bottom bar, NavHost)
            ApplicationTheme {
                BottomNavigationBar(startRoute = initialRoute)
            }
        }
    }
}

/**
 * Simple sample composable used by the Preview below.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

/**
 * Design-time preview for the Greeting composable (not used at runtime).
 */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ApplicationTheme {
        Greeting("Android")
    }
}
