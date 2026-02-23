package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Unified Animation System for the App.
 * Design Standard: "Fast, Clean, Directional"
 */
object AppMotion {
    // --- CONFIGURATION ---
    private const val DURATION_ENTER = 350
    private const val DURATION_EXIT = 300
    private const val DURATION_FADE = 250
    private const val OFFSET_FRACTION = 15 // More subtle slide
    
    // Easing
    private val standardEasing = FastOutSlowInEasing

    // --- 1. SHEET / MODAL TRANSITIONS (Current "Whiteboard" Style) ---
    // Use this when opening a detail view that feels like a "sheet" or "layer" above the current context.
    
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

    // BACKGROUND for Sheet (The content BEHIND the sheet)
    // It should recede slightly to create depth.

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

        
    // --- 2. PUSH / POP TRANSITIONS (Standard Navigation) ---
    // Use this for "Forward/Back" navigation between peer pages.
    
    /** Forward: Enter from RIGHT */
    val pushEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { w -> w / OFFSET_FRACTION },
            animationSpec = tween(DURATION_ENTER, easing = standardEasing)
        ) + fadeIn(animationSpec = tween(DURATION_FADE))

    /** Forward: Exit to LEFT */
    val pushExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { w -> -w / OFFSET_FRACTION },
            animationSpec = tween(DURATION_EXIT, easing = standardEasing)
        ) + fadeOut(animationSpec = tween(DURATION_FADE))

    /** Back: Enter from LEFT */
    val popEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { w -> -w / OFFSET_FRACTION },
            animationSpec = tween(DURATION_ENTER, easing = standardEasing)
        ) + fadeIn(animationSpec = tween(DURATION_FADE))

    /** Back: Exit to RIGHT */
    val popExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { w -> w / OFFSET_FRACTION },
            animationSpec = tween(DURATION_EXIT, easing = standardEasing)
        ) + fadeOut(animationSpec = tween(DURATION_FADE))
}
