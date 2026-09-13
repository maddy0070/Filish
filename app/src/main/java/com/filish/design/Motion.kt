package com.filish.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * FILISH motion.
 *
 * Every animation in this application has to answer one question: *why does
 * this move?* If the honest answer is "because it looked nice", it was
 * deleted. What survives falls into four jobs:
 *
 *   CONTINUITY  - the thing you are now looking at is the thing you tapped.
 *   CAUSATION   - this changed because you did that.
 *   PROGRESS    - work is happening, and this much of it is done.
 *   CONSEQUENCE - something was destroyed or created; the space reacted.
 *
 * Durations are short and differentiated by job rather than uniform. A
 * selection is not a navigation and must not feel like one: selection is a
 * change of state in something already on screen, so it is nearly immediate,
 * while navigation is a change of place and needs long enough for the eye to
 * follow the displacement.
 *
 * All of it collapses under reduced-motion. See [Motion.reduced].
 */
@Immutable
object Motion {

    /**
     * Deceleration for anything entering or settling. Aggressive front, long
     * tail: the object arrives fast and then eases into place, which reads as
     * weight rather than as a slide.
     */
    val enter: Easing = CubicBezierEasing(0.16f, 0f, 0f, 1f)

    /** Things leaving accelerate away - they do not need to be watched. */
    val exit: Easing = CubicBezierEasing(0.4f, 0f, 0.9f, 0.2f)

    /** Symmetric, for values that are being adjusted rather than moved. */
    val adjust: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** State change on something already on screen. Perceptually immediate. */
    const val QUICK = 120

    /** The default. Enough to follow, short enough not to wait for. */
    const val BASE = 220

    /** Motion carrying real information - a value settling, a list regrouping. */
    const val CONSIDERED = 320

    /** Consequence. Destruction and creation are allowed to take their time. */
    const val DELIBERATE = 440

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK, easing = adjust)
    fun <T> base(): FiniteAnimationSpec<T> = tween(BASE, easing = enter)
    fun <T> considered(): FiniteAnimationSpec<T> = tween(CONSIDERED, easing = enter)
    fun <T> leaving(): FiniteAnimationSpec<T> = tween(BASE, easing = exit)

    /**
     * For anything the user is directly manipulating, or that should feel
     * physical rather than timed. Critically damped - no overshoot. FILISH
     * never bounces: a bounce implies elasticity, and files are not elastic.
     */
    fun <T> physical(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /**
     * The reduced-motion substitute. Not "no feedback" - feedback is how the
     * interface stays honest - but all positional and scaling motion is
     * replaced by a fast tonal cross-fade, and continuous animations become
     * discrete state.
     */
    const val REDUCED = 90
    fun <T> reduced(): FiniteAnimationSpec<T> = tween(REDUCED, easing = adjust)

    /** Resolves a spec against the user's reduced-motion preference. */
    fun <T> respecting(reduceMotion: Boolean, full: () -> FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reduceMotion) reduced() else full()
}
