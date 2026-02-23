package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch

/**
 * High-performance click modifier that mimics system-level "Press" via Alpha.
 * 
 * WHY THIS IS FASTER THAN Material .clickable:
 * 1. No Ripple (Ripple requires expensive canvas drawing and animation state).
 * 2. No InteractionSource (Avoids Flow collection overhead).
 * 3. No Indication (Avoids wrapper layers).
 * 4. Uses `graphicsLayer` property setting, which bypasses Recomposition and Layout phases,
 *    updating only the RenderNode properties (RenderThread).
 */
fun Modifier.alphaClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this
    
    val scope = rememberCoroutineScope()
    // Animatable is efficient here because its value read inside graphicsLayer {} 
    // triggers Layer update, not Composition.
    val alphaAnim = remember { Animatable(1f) }
    
    this
        .graphicsLayer {
            alpha = alphaAnim.value
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    // 1. Immediate Feedback: Animate to pressed state
                    // Fire-and-forget coroutine is cleaner than state management here
                    scope.launch { 
                        alphaAnim.animateTo(0.88f, animationSpec = tween(50)) 
                    }
                    
                    tryAwaitRelease()
                    
                    // 2. Release: Animate back to normal
                    scope.launch { 
                        alphaAnim.animateTo(1f, animationSpec = tween(100)) 
                    }
                },
                onTap = { onClick() }
            )
        }
}
