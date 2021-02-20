package org.openrndr.animatable

import org.openrndr.animatable.easing.Easer
import org.openrndr.animatable.easing.Easing
import org.openrndr.events.Event
import org.openrndr.math.LinearType
import java.lang.reflect.Field
import java.util.*
import kotlin.math.max
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0

/*
Copyright (c) 2012, Edwin Jakobs
Copyright (c) 2013, Edwin Jakobs
Copyright (c) 2020, Edwin Jakobs
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/


private val globalAnimator by lazy { Animatable() }


@Suppress("unused", "MemberVisibilityCanBePrivate")
open class Animatable {

    /**
     * create an animation group
     * @param builder the animation group builder function
     */
    @JvmName("animateUnitProperty")
    fun KMutableProperty0<Unit>.animationGroup(builder: (Animatable.() -> Unit)): PropertyAnimationKey<Unit> {
        pushTime()
        val before = propertyAnimationKeys.map { it }
        this@Animatable.builder()
        val added = propertyAnimationKeys subtract before

        val groupDurationInNs = added.maxByOrNull { it.endInNs }?.let {
            it.endInNs - createAtTimeInNs
        } ?: 0L
        val uak = UnitAnimationKey(this, Unit, groupDurationInNs, createAtTimeInNs)
        uak.cancelled.listen {
            for (key in added) {
                key.cancelled.trigger(AnimationEvent())
            }
            this@Animatable.propertyAnimationKeys.removeAll(added)
        }
        propertyAnimationKeys.add(uak)
        popTime()
        return uak
    }

    /**
     * animate [Double] property
     * @param targetValue the target animation value
     * @param durationInMs animation duration in milliseconds
     * @param easing the easing to use during the animation, default is [Easing.None]
     * @param predelayInMs time to wait in milliseconds before the animation starts
     */
    @JvmName("animateProp")
    fun KMutableProperty0<Double>.animate(targetValue: Double, durationInMs: Long, easing: Easing = Easing.None, predelayInMs: Long = 0): PropertyAnimationKey<Double> {
        return animate(this, targetValue, durationInMs, easing, predelayInMs)
    }

    /**
     * animate [LinearType] property
     * @param targetValue the target animation value
     * @param durationInMs animation duration in milliseconds
     * @param easing the easing to use during the animation, default is [Easing.None]
     * @param predelayInMs time to wait in milliseconds before the animation starts
     */
    @JvmName("animatePropLinearType")
    fun <T : LinearType<T>> KMutableProperty0<T>.animate(targetValue: T, durationInMs: Long, easing: Easing = Easing.None, predelayInMs: Long = 0): PropertyAnimationKey<T> {
        return animate(this, targetValue, durationInMs, easing, predelayInMs)
    }

    internal fun MutableList<PropertyAnimationKey<*>>.cancel(property: KMutableProperty<*>) {
        val toCancel = filter { it.property == property }
        for (key in toCancel) {
            key.cancelled.trigger(AnimationEvent())
        }
        removeAll(toCancel)
    }

    /**
     * cancel all animation groups on [Unit] property
     */
    @JvmName("cancelUnitProperty")
    fun KMutableProperty0<Unit>.cancel() = propertyAnimationKeys.cancel(this)

    /**
     * cancel all animations on [Double] property
     */
    @JvmName("cancelDoubleProperty")
    fun KMutableProperty0<Double>.cancel() = propertyAnimationKeys.cancel(this)

    /**
     * cancel all animations on [LinearType] property
     */
    @JvmName("cancelLinearTypeProperty")
    fun <T : LinearType<T>> KMutableProperty0<T>.cancel() = propertyAnimationKeys.cancel(this)


    @JvmName("completeUnitProperty")
    fun KMutableProperty0<Unit>.complete() {
        propertyAnimationKeys.findLast { it.property == this }?.let {
            createAtTimeInNs = it.startInNs + it.durationInNs
        }
    }

    @JvmName("completeDoubleProperty")
    fun KMutableProperty0<Double>.complete() {
        propertyAnimationKeys.findLast { it.property == this }?.let {
            createAtTimeInNs = it.startInNs + it.durationInNs
        }
    }

    @JvmName("completeLinearTypeProperty")
    fun <T : LinearType<T>> KMutableProperty0<T>.complete() {
        propertyAnimationKeys.findLast { it.property == this }?.let {
            createAtTimeInNs = it.startInNs + it.durationInNs
        }
    }

    /**
     * the last queued or active animation group for [Unit] property, or null if no animations are available
     */
    val KMutableProperty0<Unit>.last: PropertyAnimationKey<Unit>?
        @JvmName("lastUnitProperty")
        get() = propertyAnimationKeys.findLast { it.property == this } as PropertyAnimationKey<Unit>?

    /**
     * the last queued or active animation for [LinearType] property, or null if no animations are available
     */
    val <T : LinearType<T>> KMutableProperty0<T>.last: PropertyAnimationKey<T>?
        @JvmName("lastLinearTypeProperty")
        get() = propertyAnimationKeys.findLast { it.property == this } as PropertyAnimationKey<T>?

    /**
     * the last queued or active animation for [Double] property, or null if no animations are available
     */
    val KMutableProperty0<Double>.last: PropertyAnimationKey<Double>?
        @JvmName("lastDoubleProperty")
        get() = propertyAnimationKeys.findLast { it.property == this } as PropertyAnimationKey<Double>?

    /**
     * check if [Unit] property has queued or active animation groups
     */
    val KMutableProperty0<Unit>.hasAnimations
        @JvmName("hasAnimationsUnitProperty")
        get() = propertyAnimationKeys.find { it.property == this } != null

    /**
     * check if [Double] property has queued or active animation groups
     */
    val KMutableProperty0<Double>.hasAnimations
        @JvmName("hasAnimationsDoubleProperty")
        get() = propertyAnimationKeys.find { it.property == this } != null

    /**
     * check if [LinearType] property has queued or active animation groups
     */
    val <T : LinearType<T>>  KMutableProperty0<T>.hasAnimations
        @JvmName("hasAnimationsLinearTypeProperty")
        get() = propertyAnimationKeys.find { it.property == this } != null

    internal fun <T> List<PropertyAnimationKey<*>>.durationInMs(property: KMutableProperty0<T>) =
            filter { it.property == property }.maxByOrNull { it.endInNs }?.let {
                (it.endInNs - lastTimeInNs) / 1000L
            } ?: 0L

    /**
     * remaining duration of queued and activate animation groups for [Unit] property
     */
    val KMutableProperty0<Unit>.durationInMs: Long
        @JvmName("durationInMsUnit")
        get() = propertyAnimationKeys.durationInMs(this)

    /**
     * remaining duration of queued and active animations for [Double] property
     */
    val KMutableProperty0<Double>.durationInMs: Long
        @JvmName("durationInMsDouble")
        get() = propertyAnimationKeys.durationInMs(this)

    /**
     * remaining duration of queued and active animations for [LinearType] property
     */
    val <T : LinearType<T>> KMutableProperty0<T>.durationInMs: Long
        @JvmName("durationInMsLinearTypeProperty")
        get() = propertyAnimationKeys.durationInMs(this)

    /**
     * the time at which created animations will start
     */
    var createAtTimeInNs: Long = clock.timeNanos
        private set

    internal var lastTimeInNs = createAtTimeInNs

    private var animationKeys: MutableList<AnimationKey>? = mutableListOf()
    private var propertyAnimationKeys: MutableList<PropertyAnimationKey<*>> = mutableListOf()

    private var stage: String? = null

    private var timeStack: Stack<Long>? = null

    private var animatable: Any? = null

    @Deprecated("no longer needed for property based API")
    private val fieldCache = HashMap<String, Field>()

    @Deprecated("no longer needed for property based API")
    constructor(target: Any) {
        animatable = target
        //   animationKeys!!.ensureCapacity(10)
    }

    constructor() {
        // animationKeys!!.ensureCapacity(10)
        animatable = this
    }

    constructor(createAtTime: Long) {
        this.createAtTimeInNs = createAtTime
        //animationKeys!!.ensureCapacity(10)
        animatable = this
    }

    fun pushTime() {
        if (timeStack == null) {
            timeStack = Stack()
        }
        timeStack!!.push(createAtTimeInNs)
    }

    fun popTime() {
        createAtTimeInNs = timeStack!!.pop()
    }

    /**
     * Wait for a given time before cueing the next animation.
     * @param delayInMs the delay in milliseconds
     * @return `this` for easy animation chaining
     */
    fun delay(delayInMs: Long, delayInNs: Long = 0) {
        createAtTimeInNs += delayInMs * 1_000 + delayInNs
    }

    /**
     * Wait until a the given timeMillis
     * @param timeInMs the timeMillis in milliseconds
     * @return `this` for easy animation chaining
     */
    fun waitUntil(timeInMs: Long, timeInNs: Long = 0) {
        createAtTimeInNs = timeInMs * 1_000 + timeInNs
    }


    /**
     * Animates a single variable.
     * @param variable the name of the variable to animate
     * @param target the target value
     * @param durationMillis the durationMillis of the animation in milliseconds
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun animate(variable: String, target: Double, durationMillis: Long) {
        return animate(variable, target, durationMillis, Easing.None)
    }

    /**
     * Animates a single variable.
     * @param variable the name of the variable to animate
     * @param target the target value
     * @param durationMillis the durationMillis of the animation in milliseconds
     * @param easing the easing to use during the animation
     * @return `this` for easy animation chaining
     * Also arrays of floats or doubles can be animated
     * <pre>animatable.animate("x[0]", 100, 1000).complete().animate("x[0]", 0, 1000);</pre>
     */

    @Deprecated("use property based API")
    fun animate(variable: String, target: Double, durationMillis: Long, easing: Easing) {
        return animate(variable, target, durationMillis, easing.easer)
    }

    /**
     * Animates a single variable.
     * @param variable the name of the variable to animate
     * @param target the target value
     * @param durationMillis the durationMillis of the animation in milliseconds
     * @param easer the easing to use during the animation
     * @return `this` for easy animation chaining
     * Also arrays of floats or doubles can be animated
     * <pre>animatable.animate("x[0]", 100, 1000).complete().animate("x[0]", 0, 1000);</pre>
     */
    @Deprecated("use property based API")
    fun animate(variable: String, target: Double, durationMillis: Long, easer: Easer) {
        val key = AnimationKey(variable, target, durationMillis * 1000, createAtTimeInNs)
        key.easing = easer
        if (animationKeys == null) {
            animationKeys = mutableListOf()
        }
        animationKeys!!.add(key)
    }


    /**
     * Queries if animations are cued.
     * @return `true` iff animations are cued.
     */
    fun hasAnimations(): Boolean {
        return (animationKeys?.size ?: 0) + propertyAnimationKeys.size != 0
    }

    /**
     * Queries if animations matching any of the given variables are cued.
     * @param variables
     * @return `true` iff animations matching any of the given variables are cued.
     */
    @Deprecated("use property based API")
    fun hasAnimations(vararg variables: String): Boolean {
        val variableSet = HashSet<String>()
        for (variable in variables) {
            variableSet.add(variable)
        }
        return animationKeys?.any { variableSet.contains(it.variable) } ?: false
    }

    /**
     * Cues an additive animation for a single variable.
     * @param variable the target animation
     * @param target the target value
     * @param duration the duration of the animation in milliseconds
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun add(variable: String, target: Double, duration: Long) {
        add(variable, target, duration, Easing.None)
    }

    /**
     * Cues an additive animation for a single variable using a given easing mode.
     * @param variable the target animation
     * @param target the target value
     * @param durationMillis the durationMillis of the animation in milliseconds
     * @param easing the easing to use for this animation
     * @return `this` for easy animation chaining
     *
     * Example usage:
     * <pre>
     * `animatable.add("x", 100, 1000);
     * animatable.complete();
     * animatable.add("x", 0, 1000);`
    </pre> *
     */
    @Deprecated("use property based API")
    fun add(variable: String, target: Double, durationMillis: Long, easing: Easing) {
        add(variable, target, durationMillis, easing.easer)
    }

    @Deprecated("use property based API")
    fun add(variable: String, target: Double, durationMillis: Long, easer: Easer) {
        val key = AnimationKey(variable, target, durationMillis * 1000, createAtTimeInNs)
        key.animationMode = AnimationKey.AnimationMode.Additive
        key.easing = easer
        animationKeys!!.add(key)
    }

    @Deprecated("use property based API")
    fun complete(variable: String) {
        val key = lastQueued(variable)
        if (key != null) {
            createAtTimeInNs = key.start + key.duration

        }
    }

    /**
     * Wait for the last cued animation to complete before starting the next animation and install
     * a completion call back that is called when the last queued animation ends.
     *
     * Example usage:
     * <pre>
     * `AnimationCompletedCallBack callback = new AnimationCompletedCallBack() {
     * public void animationCompleted(Animatable target) {
     * System.out.println("animation completed");
     * }
     * }
     *
     * animatable.animate("x", 100, 1000);
     * animatable.complete(callback);
     * animatable.animate("x", 0, 1000);`
    </pre> *
     *
     * @param callback the callback to call when the animation is completed
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun complete(callback: (Animatable) -> Unit) {
        return complete(this, callback)
    }


    /**
     * Sets the easing mode for the last cued animation.
     *
     *
     * Example usage:
     * <pre>
     * `animatable.animate("x", 100, 1000).ease(Easing.BounceOut);
     * animatable.complete();
     * animatable.animate("x", 0, 1000).ease(Easing.BounceIn);`
    </pre> *
     *
     * @param easing the easing mode to set
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun ease(easing: Easing) {
        return ease(easing.easer)
    }

    @Deprecated("use property based API")
    fun ease(easer: Easer) {
        val last = lastQueued()
        if (last != null) {
            last.easing = easer
        }
    }

    /**
     * Returns the current velocity of the animating variable
     * @param variable the variable to give the velocity for
     * @return velocity in units per second
     */
    @Deprecated("use property based API")
    fun velocity(variable: String): Double {
        for (key in animationKeys!!) {
            if (key.start <= lastTimeInNs && key.variable.equals(variable)) {
                val delta = key.target - key.from
                val dt = (lastTimeInNs - key.start) / 1E6
                return key.easing.velocity(dt, key.from, delta, key.durationSeconds)
            }
        }
        return 0.0
    }

    /**
     * Wait for the last cued animation in the given animatable to complete before starting the next animation.
     * @param animatable the animatable to wait for
     */
    @Deprecated("use property based API")
    fun complete(animatable: Animatable) {
        //noinspection unchecked,RedundantCast
        return complete(animatable, null)
    }


    /**
     * Wait for the animation to complete in the given animatable to complete before starting the next animation and install
     * a completion call back that is called when the last queued animation ends.
     * @param animatable the animatable to wait for
     * @param callback the callback to call when the animation is completed
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun complete(animatable: Animatable = this, callback: ((Animatable) -> Unit)? = null) {
        val waitForKey = animatable.lastQueued()

        if (waitForKey != null) {
            createAtTimeInNs = waitForKey.start + waitForKey.duration
            if (callback != null)
                animationKeys?.last()?.addCompletionCallback(callback)
        } else {
            callback?.invoke(this)
        }
    }

    /**
     * Cancels all animations.
     * @return `this` for easy animation chaining
     */
    fun cancel() {
        animationKeys?.clear()
        propertyAnimationKeys.clear()
        createAtTimeInNs = lastTimeInNs
    }


    /**
     * Cancels all queued animations. Running animations will run until end.
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun cancelQueued() {
        val keep = mutableListOf<AnimationKey>()
        animationKeys?.let { keys ->
            keys.filterTo(keep) { it.animationState == AnimationKey.AnimationState.Playing }
            keys.clear()
            keys.addAll(keep)
        }
    }


    /**
     * Ends running animations and cancels all cued animations. The behaviour of end() differs from cancel() in how animated variables are treated, end() sets the animated variables to their target value. Using end() will produce a pop in the animation.
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun end() {
        for (key in animationKeys!!) {
            if (key.animationState == AnimationKey.AnimationState.Playing) {
                setFieldValue(key.variable, key.target)
            }
        }

        animationKeys?.clear()
        createAtTimeInNs = lastTimeInNs
    }

    /**
     * Cancels selected animations
     * <pre>
     * animatable.animate("x", 100, 1000);
     * animatable.animate("y", 100, 1000);
     * animatable.cancel(new String["x"]);
    </pre> *
     * @param fields the names of the fields for which animations should be cancelled
     * @return `this` for easy animation chaining
     */
    @Deprecated("use property based API")
    fun cancel(fields: Array<String>) {

        val filter = HashSet<String>()
        Collections.addAll(filter, *fields)

        val toRemove = ArrayList<AnimationKey>()
        for (key in animationKeys!!) {
            if (filter.contains(key.variable)) {
                toRemove.add(key)
            }
        }

        animationKeys?.removeAll(toRemove)
        createAtTimeInNs = lastTimeInNs
    }

    @Deprecated("use property based API")
    private fun getField(variable: String): Field? {
        var field: Field? = fieldCache[variable]

        if (field == null) {
            if (animatable == null) {
                animatable = this
            }
            var `class`: Class<*>? = animatable?.javaClass

            while (`class` != null) {
                try {
                    field = `class`.getDeclaredField(variable)
                    field!!.isAccessible = true
                    fieldCache.put(variable, field)
                    return field
                } catch (e: NoSuchFieldException) {
                    `class` = `class`.superclass
                }
            }
        }
        return field
    }

    @Deprecated("use property based API")
    private fun getFieldValue(variable: String): Double {
        //getFieldValue(this::lastTime)

        try {

            val indexPos = if (variable[variable.length - 1] == ']') variable.indexOf("[") else -1
            if (indexPos != -1) {

                val fieldName = variable.substring(0, indexPos)
                val keyName = variable.substring(indexPos + 1, variable.length - 1)
                val field = getField(fieldName)

                val _class = field!!.type

                if (_class == DoubleArray::class.java) {
                    val offset = Integer.parseInt(keyName)
                    val a = field.get(animatable) as DoubleArray
                    return a[offset]
                } else if (_class == FloatArray::class.java) {
                    val offset = Integer.parseInt(keyName)
                    val a = field.get(animatable) as FloatArray
                    return a[offset].toDouble()
                } else if (field.get(this) is List<*>) {
                    val offset = Integer.parseInt(keyName)
                    val a = field.get(this) as List<*>
                    val o = a[offset]
                    if (o is Double) {
                        return o
                    } else if (o is Float) {
                        return o.toDouble()
                    }
                }
            } else {
                val field = getField(variable)
                if (field != null) {
                    return field.getDouble(animatable)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        return 0.0
    }

    private fun getFieldValue(variable: KMutableProperty0<Any>): Double {
        val g = variable.get()
        when (g) {
            is Double -> return g.toDouble()
        }
        return 0.0
    }


    @Deprecated("use property based API")
    private fun lastQueued(): AnimationKey? {
        return if (animationKeys!!.size > 0) {
            animationKeys!![animationKeys!!.size - 1]
        } else
            null
    }

    @Deprecated("use property based API")
    private fun lastQueued(variable: String): AnimationKey? {
        for (i in animationKeys!!.indices.reversed()) {
            val k = animationKeys!![i]
            if (variable == k.variable) {
                return k
            }
        }
        return null
    }

    @Deprecated("old JVM reflection based API")
    private fun setFieldValue(variable: String, value: Double) {
        try {
            val indexPos = if (variable[variable.length - 1] == ']') variable.indexOf("[") else -1
            if (indexPos != -1) {

                val fieldName = variable.substring(0, indexPos)
                val keyName = variable.substring(indexPos + 1, variable.length - 1)

                val field = getField(fieldName)
                val _class = field!!.type
                field.isAccessible = true
                if (_class == DoubleArray::class.java) {
                    val offset = Integer.parseInt(keyName)
                    val a = field.get(animatable) as DoubleArray
                    a[offset] = value
                } else if (_class == FloatArray::class.java) {
                    val offset = Integer.parseInt(keyName)
                    val a = field.get(animatable) as FloatArray
                    a[offset] = value.toFloat()
                } else if (field.get(animatable) is MutableList<*>) {
                    val offset = Integer.parseInt(keyName)
                    val list = field.get(animatable) as MutableList<*>
                    val o = list[offset]
                    if (o is Float) {
                        @Suppress("UNCHECKED_CAST")
                        val floatList = field.get(animatable) as MutableList<Float>
                        floatList[offset] = value.toFloat()
                    } else if (o is Double) {
                        @Suppress("UNCHECKED_CAST")
                        val doubleList = field.get(animatable) as MutableList<Double>
                        doubleList[offset] = value
                    }
                }
            } else {
                val field = getField(variable)

                if (field != null) {
                    if (field.type == Double::class.javaPrimitiveType) {
                        field.setDouble(animatable, value)
                    } else if (field.type == Float::class.javaPrimitiveType) {
                        field.setFloat(animatable, value.toFloat())
                    } else {
                        System.err.println("warning: could not set animatable value")
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

    }


    private fun blend(easing: Easer, dt: Double, from: Double, delta: Double): Double {
        return easing.ease(dt, from, delta, 1.0)
    }


    /**
     * Returns the duration of the animation in milliseconds
     * @return the duration of the running plus queued animations
     */
    @Deprecated("use property based API")
    fun duration(): Long {
        var latest: Long = 0
        for (key in this.animationKeys!!) {
            val end = key.start + key.duration
            if (end > latest) {
                latest = end
            }
        }

        return max(0L, max(latest, createAtTimeInNs) - lastTimeInNs)
    }

    /**
     * Updates the animation state with a user supplied time
     * @param timeInNs the time to use for updating the animation state
     */
    @JvmOverloads
    fun updateAnimation(timeInNs: Long = clock.timeNanos) {
        val toRemove = mutableListOf<AnimationKey>()
        val calls = mutableListOf<Pair<(Animatable) -> Unit, Animatable>>()

        lastTimeInNs = timeInNs
        createAtTimeInNs = lastTimeInNs


        if (animationKeys == null) {
            animationKeys = mutableListOf()
        }
        for (key in animationKeys!!) {
            if (key.start == -1L) {
                key.start = timeInNs
            }

            if (key.start <= timeInNs) { // && key.start + key.duration >= time) {
                if (key.animationState == AnimationKey.AnimationState.Queued) {
                    key.play(getFieldValue(key.variable))
                }

                if (key.animationState == AnimationKey.AnimationState.Playing) {
                    var dt = (timeInNs - key.start).toDouble()

                    if (key.duration > 0) {
                        dt /= key.duration
                    } else {
                        dt = 1.0
                    }

                    if (dt < 0)
                        dt = 0.0
                    if (dt >= 1) {
                        dt = 1.0
                        key.stop()
                    }

                    if (key.animationMode == AnimationKey.AnimationMode.Blend) {
                        val value = blend(key.easing, dt, key.from, key.target - key.from)
                        setFieldValue(key.variable, value)
                    } else if (key.animationMode == AnimationKey.AnimationMode.Additive) {
                        val value = blend(key.easing, dt, key.from, key.target)
                        setFieldValue(key.variable, value)
                    }
                }
                if (key.animationState == AnimationKey.AnimationState.Stopped) {
                    for (callback in key.completionCallbacks) {
                        calls.add(Pair(callback, this))
                    }
                    toRemove.add(key)
                }
            }
        }
        for (call in calls) {
            call.first(call.second)
        }

        for (key in toRemove) {
            animationKeys!!.remove(key)
        }

        updatePropertyAnimations(timeInNs)


    }

    private fun updatePropertyAnimations(timeInNs: Long = clock.timeNanos) {
        val toRemove = mutableListOf<PropertyAnimationKey<*>>()
        val triggers = mutableListOf<Event<AnimationEvent>>()

        for (key in propertyAnimationKeys) {
            if (key.startInNs <= timeInNs) { // && key.start + key.duration >= time) {
                if (key.animationState == AnimationKey.AnimationState.Queued) {
                    key.play()
                }

                if (key.animationState == AnimationKey.AnimationState.Playing) {
                    var dt = (timeInNs - key.startInNs).toDouble()

                    if (key.durationInNs > 0) {
                        dt /= key.durationInNs
                    } else {
                        dt = 1.0
                    }

                    if (dt < 0)
                        dt = 0.0
                    if (dt >= 1) {
                        dt = 1.0
                        key.stop()
                    }
                    key.applyToProperty(dt)
                }
                if (key.animationState == AnimationKey.AnimationState.Stopped) {
                    triggers.add(key.completed)
                    toRemove.add(key)
                }
            }
        }
        for (event in triggers) {
            event.trigger(AnimationEvent())
        }

        for (key in toRemove) {
            propertyAnimationKeys.remove(key)
        }

        lastTimeInNs = timeInNs

        if (lastTimeInNs > createAtTimeInNs) {
            createAtTimeInNs = lastTimeInNs
        }
    }


    /**
     * Returns the number of playing plus queued animations
     * @return number of playing plus queued animations
     */
    fun animationCount(): Int {
        return animationKeys!!.size + propertyAnimationKeys.size
    }

    companion object {
        internal var clock: Clock = DefaultClock()

        /**
         * Globally sets the clock object to use. The default is a `DefaultClock` instance
         * @param clock a `a Clock instance`
         */
        fun clock(clock: Clock) {
            Animatable.clock = clock
        }

        /**
         * Returns the global clock object
         * @return this
         */
        fun clock(): Clock {
            return clock
        }

        fun array(variable: String, index: Int): String {
            return "$variable[$index]"
        }
    }

    fun <T : LinearType<T>> animate(variable: KMutableProperty0<T>, target: T, durationMillis: Long,
                                    easing: Easing = Easing.None, predelayInMs: Long = 0): PropertyAnimationKey<T> {
        val key = LinearTypeAnimationKey(variable, target, durationMillis * 1000, createAtTimeInNs + predelayInMs * 1000, easing)
        propertyAnimationKeys.add(key)
        return key
    }

    fun animate(variable: KMutableProperty0<Double>, target: Double, durationMillis: Long,
                easing: Easing = Easing.None, predelayInMs: Long = 0): PropertyAnimationKey<Double> {
        val key = DoubleAnimationKey(variable, target, durationMillis * 1000, createAtTimeInNs + predelayInMs * 1000, easing)
        propertyAnimationKeys.add(key)
        return key
    }

}

//fun animate(builder: Animatable.() -> Unit) {
//    globalAnimator.builder()
//}

