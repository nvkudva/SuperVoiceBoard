// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.voice

/**
 * What the user can do about a voice error.
 *
 * VBoard nested this inside `VoiceBarView`, which is not ported: voice is a
 * mode of HeliBoard's existing suggestion strip, not a bar of its own
 * (PLAN.md §2). The enum is a session-layer concept — it says what went wrong
 * and what the recovery is — so it lives here, and whatever view is mounted
 * renders it.
 */
enum class VoiceErrorAction { OPEN_PERMISSION, OPEN_DOWNLOAD, DISMISS }
