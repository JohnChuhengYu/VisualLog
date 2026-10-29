package com.visuallog.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Unified Animation System for the App.
 * Design Standard: "Fast, Clean, Directional"
 */
object AppMotion {
    private const val DURATION_ENTER = 350
    private const val DURATION_EXIT = 300
    private const val DURATION_FADE = 250
    private const val OFFSET_FRACTION = 15
    
    private val standardEasing = FastOutSlowInEasing

    /** Slide UP from bottom + Fade In */
    val sheetEnter: EnterTransition = 
        slideInVertically(
            initialOffsetY = { h -> h / OFFSET_FRACTION },
            animationSpec = tween(DURATION_ENTER, easing = standardEasing)
        ) + fadeIn(
            animationSpec = tween(DURATION_FADE)
        )

    /** Slide DOWN to bottom + Fade Out */
    val sheetExit: ExitTransition = 
        slideOutVertically(
            targetOffsetY = { h -> h / OFFSET_FRACTION },
            animationSpec = tween(DURATION_EXIT, easing = standardEasing)
        ) + fadeOut(
            animationSpec = tween(DURATION_FADE)
        )

    /** Restore from background (Fade In Only) */
    val sheetPopEnter: EnterTransition = 
        fadeIn(
            animationSpec = tween(DURATION_FADE)
        )

    /** Recede to background (Fade Out Only) */
    val sheetPopExit: ExitTransition = 
        fadeOut(
            animationSpec = tween(DURATION_FADE)
        )
}

