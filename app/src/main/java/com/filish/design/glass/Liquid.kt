package com.filish.design.glass

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import com.filish.design.Motion

/**
 * Liquid motion.
 *
 * ===========================================================================
 * What "liquid" is allowed to mean here
 * ===========================================================================
 *
 * The obvious reading of a liquid interface is wobble: rubbery overshoot,
 * blobs merging, things that jiggle when touched. FILISH rejects that outright
 * and the reason is not taste. Elasticity is a claim about the material, and a
 * file is not elastic - a 40 GB video does not boing. An interface that says
 * it does is lying about the thing it is supposed to be managing, and the lie
 * gets tiring by the second week, which is when a file manager actually gets
 * used.
 *
 * So liquid here means the three properties of a liquid that have nothing to
 * do with bouncing:
 *
 *   VISCOSITY       resistance to sudden change. Motion starts reluctantly
 *                   and lets go slowly. It never snaps.
 *   FINDING LEVEL   a quantity settles toward a value asymptotically rather
 *                   than arriving at it. This is how every measurement in the
 *                   application behaves, because every measurement in the
 *                   application is genuinely still being computed while you
 *                   watch it.
 *   SURFACE TENSION a boundary lags behind the body it bounds. The meniscus
 *                   reaches its level slightly after the volume does.
 *
 * All three are honest about the underlying system. A folder size really is
 * converging; a transfer really is filling something up. That is the whole
 * argument for this vocabulary: FILISH's data settles, so FILISH's motion
 * settles. If the data were instant the motion would be instant.
 *
 * [Motion] keeps its four jobs - continuity, causation, progress, consequence
 * - and every duration there still applies. This adds the physics for the one
 * new thing V3 introduces: material with a level.
 */
@Immutable
object Liquid {

    /**
     * The viscous curve. Reluctant to start, slow to let go.
     *
     * Compare [Motion.enter], which is aggressive at the front: that is right
     * for something arriving on screen, because you want it there NOW and
     * settling is just politeness. This is the opposite shape, for something
     * whose value is changing - it should look like it took effort.
     */
    val viscous: Easing = CubicBezierEasing(0.45f, 0f, 0.12f, 1f)

    /**
     * Response to the finger. The one place liquid is not viscous.
     *
     * Touch feedback that eases in feels broken, because the user knows
     * exactly when they touched the screen and any delay reads as lag rather
     * than as material. A lens under a finger responds immediately and relaxes
     * slowly.
     */
    val wake: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** A level changing under its own weight. Slow on purpose: this is the
     *  one place where slowness reads as mass rather than as waiting. */
    const val LEVEL = 620

    /** Surface tension: how far the meniscus trails the body it bounds. */
    const val TENSION_LAG = 70

    /** A band responding to selection or focus. Perceptually immediate. */
    const val BAND = Motion.QUICK

    /** A lens lifting off the substrate, or settling back onto it. */
    const val LIFT = 160

    /**
     * A value converging on its true figure - a folder size resolving, a
     * duplicate scan narrowing, storage recalculating after a delete.
     *
     * Critically damped, deliberately soft. It must never overshoot, because
     * an overshooting number has briefly displayed a figure that is not true,
     * and in a file manager that is a correctness bug wearing a motion
     * costume.
     */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)

    /** The meniscus finding its level. */
    fun <T> level(): FiniteAnimationSpec<T> = tween(LEVEL, easing = viscous)

    /** The meniscus, trailing the volume it bounds. */
    fun <T> meniscus(): FiniteAnimationSpec<T> =
        tween(LEVEL, delayMillis = TENSION_LAG, easing = viscous)

    /** Touch response on a lens. */
    fun <T> touch(): FiniteAnimationSpec<T> = tween(LIFT, easing = wake)

    /** The band changing state. */
    fun <T> band(): FiniteAnimationSpec<T> = tween(BAND, easing = Motion.adjust)

    /**
     * Reduced motion.
     *
     * Not "nothing moves" - feedback is how the interface stays honest, and a
     * user who has asked for less motion has not asked to be told less. What
     * changes is the KIND of feedback:
     *
     *   the level    jumps to its value; the number beside it still updates
     *   the meniscus stops trailing, because the trail is the only decorative
     *                part of it
     *   the band     steps between states instead of sliding
     *   a lens       changes tone instead of translating
     *
     * The information content is identical. Only the physics is dropped.
     */
    fun <T> respecting(reduceMotion: Boolean, full: () -> FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reduceMotion) Motion.reduced() else full()

    /**
     * The performance rule, written down where it can be pointed at.
     *
     * At most ONE continuously animating element may be on screen at a time,
     * and only while real work is in flight. A directory of fifty thousand
     * files that has two hundred shimmering rows is not a liquid interface, it
     * is a space heater - and on the mid-range hardware this application is
     * actually for, it is a dropped-frame machine.
     *
     * A row's band does not animate at rest. The substrate does not animate,
     * ever. The volume animates only while its figure is changing. When the
     * work finishes, the motion stops, which is also the clearest possible
     * signal that the work finished.
     */
    const val CONCURRENT_ANIMATIONS = 1
}
