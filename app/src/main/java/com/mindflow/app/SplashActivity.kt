package com.mindflow.app

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindflow.app.ui.theme.MindFlowTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(TRANSPARENT),
        )
        setContent {
            MindFlowTheme {
                MindFlowSplashScreen(
                    onFinished = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun MindFlowSplashScreen(
    onFinished: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(1800)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val titleTopPadding = maxHeight * 0.30f
        Image(
            painter = painterResource(R.drawable.mindflow_splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x50020A18),
                            0.22f to Color(0x18020A18),
                            0.58f to Color.Transparent,
                            1.0f to Color(0x12020A18),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = titleTopPadding, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.splash_line_one),
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 0.6.sp,
                    shadow = Shadow(
                        color = Color(0x66429FFF),
                        offset = Offset(0f, 8f),
                        blurRadius = 22f,
                    ),
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF6FAFF),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.splash_line_two),
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 0.4.sp,
                    shadow = Shadow(
                        color = Color(0x55429FFF),
                        offset = Offset(0f, 6f),
                        blurRadius = 18f,
                    ),
                ),
                fontWeight = FontWeight.Medium,
                color = Color(0xFFD7EBFF),
                textAlign = TextAlign.Center,
            )
        }
    }
}
