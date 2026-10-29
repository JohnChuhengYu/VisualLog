package com.visuallog.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * High-performance click modifier that mimics system-level "Press" via Alpha.
 * Bypasses Compose Recomposition and Layout phases by setting RenderNode properties directly.
 */
fun Modifier.alphaClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this
    
    val scope = rememberCoroutineScope()
    val alphaAnim = remember { Animatable(1f) }
    
    this
        .graphicsLayer {
            alpha = alphaAnim.value
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scope.launch { 
                        alphaAnim.animateTo(0.88f, animationSpec = tween(50)) 
                    }
                    tryAwaitRelease()
                    scope.launch { 
                        alphaAnim.animateTo(1f, animationSpec = tween(100)) 
                    }
                },
                onTap = { onClick() }
            )
        }
}

