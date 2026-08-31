// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.correct

import com.vboard.core.correct.FixButtonState

/**
 * The button and the message line the fix controller drives.
 *
 * VBoard's controller talked to its own `ToolbarView` directly. This fork mounts
 * the fix on HeliBoard's toolbar-key mechanism (W5.1), so the controller now
 * talks to this instead and the keyboard supplies the implementation. The
 * controller decides *what* to say and which state the key is in; the surface
 * decides how a key and a message look in this particular keyboard.
 */
interface FixSurface {
    /** Redraw the key for [state] — idle, running, undo-armed or disabled. */
    fun updateFixButton(state: FixButtonState, contentDescription: String)

    /** Transient feedback: a refusal, "nothing to change", "undone". */
    fun showFixMessage(text: String)

    /** Drop any message still showing. */
    fun clearFixMessage()
}
