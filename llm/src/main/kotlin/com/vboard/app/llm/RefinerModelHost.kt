// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.llm

/**
 * Supplies the refiner model path.
 *
 * VBoard read it off its own `Application` subclass. :llm cannot see the
 * keyboard's Application here — and must not: the whole point of this module is
 * that it runs in its own process and can be killed without taking the keyboard
 * with it. The `Application` in *whichever* process is asking implements this.
 */
interface RefinerModelHost {
    /** Absolute path of the installed refiner model, or null when none is installed. */
    fun refinerModelPath(): String?
}
