package org.openturnly.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.openturnly.mobile.ui.BusinessPickerScreen
import org.openturnly.mobile.ui.BrandLoading
import org.openturnly.mobile.ui.MainScaffold
import org.openturnly.mobile.ui.NoBusinessScreen
import org.openturnly.mobile.ui.OpenTurnlyTheme
import org.openturnly.mobile.ui.ReconnectScreen
import org.openturnly.mobile.ui.SignInScreen

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OpenTurnlyTheme { RootView(model) } }
    }
}

/** Picks the screen the model says is up. */
@Composable
fun RootView(model: AppModel) {
    LaunchedEffect(Unit) { if (model.screen == AppModel.Screen.LOADING) model.restore() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = if (model.signOutBusy) AppModel.Screen.LOADING else model.screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "root",
        ) { screen ->
            when (screen) {
                AppModel.Screen.LOADING -> BrandLoading()
                AppModel.Screen.SIGN_IN -> SignInScreen(model)
                AppModel.Screen.RECONNECT -> ReconnectScreen(model)
                AppModel.Screen.BUSINESSES -> BusinessPickerScreen(model)
                AppModel.Screen.NO_BUSINESS -> NoBusinessScreen(model)
                AppModel.Screen.MAIN -> MainScaffold(model)
            }
        }
    }
}
