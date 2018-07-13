package org.openrndr

import org.openrndr.draw.Drawer

/**
 * Defines a Program extension. This is the interface for developers of OPENRNDR extensions.
 */
interface Extension {
    var enabled:Boolean
    fun setup(program:Program) {}
    fun beforeDraw(drawer: Drawer, program:Program) {}
    fun afterDraw(drawer:Drawer, program:Program) {}
}

/**
 * Remove this extension from the rendering loop
 */
fun Extension.disable() {
    this.enabled = false
}

/**
 * Include this extension in the rendering loop
 */
fun Extension.enable() {
    this.enabled = true
}

/**
 * Toggle between enabled and disabled
 */
fun Extension.toggle() {
    this.enabled = !this.enabled
}