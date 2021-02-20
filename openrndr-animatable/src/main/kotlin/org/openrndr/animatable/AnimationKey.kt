package org.openrndr.animatable

import org.openrndr.animatable.easing.Easing

/**
 * Animation key class
 * @author Edwin Jakobs
 */

class AnimationKey(val variable: String, val target: Double, val duration: Long, var start: Long) {
    var from: Double = 0.toDouble()

    var message: Any? = null
    var stage: String? = null

    /**
     * returns all completion callbacks for the animation
     * @return a list containing completion callbacks
     */

    var completionCallbacks = mutableListOf<(Animatable)->Unit>()

    var animationState = AnimationState.Queued
        private set
    var animationMode = AnimationMode.Blend
    /**
     * returns the easing mode
     * @return the easing mode used
     */
    /**
     * sets the [Easing] mode for the animation
     */
    var easing = Easing.None.easer

    enum class AnimationMode {
        Blend,
        Additive
    }

    enum class AnimationState {
        Queued,
        Playing,
        Stopped
    }

    /**
     * plays the animation
     * @param from the current value of the variable to animate
     */
    fun play(from: Double) {
        this.from = from
        animationState = AnimationState.Playing
    }

    /**
     * stops the animation
     */
    fun stop() {
        animationState = AnimationState.Stopped
    }

    /**
     * adds a completion callback that is called when the animation ends
     * @param callback the callback to be executed
     */
    fun addCompletionCallback(callback: (Animatable)->Unit) {
        completionCallbacks.add(callback)
    }

    val durationSeconds get() = duration / 1E6

}

