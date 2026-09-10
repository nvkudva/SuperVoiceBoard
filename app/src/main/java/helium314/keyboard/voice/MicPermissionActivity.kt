// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log

/**
 * WaveKey: asks for the microphone with the system dialog.
 *
 * An input method cannot request a runtime permission — only an Activity can —
 * so the voice strip used to send people to the App info screen and hope they
 * found the Permissions entry. This activity is invisible: it opens, shows the
 * one dialog, and closes. If the system will not show the dialog any more
 * (permanently denied), it falls back to App info, which is then the only place
 * the decision can be changed.
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        // A denial with no rationale offered means the system will not ask again;
        // App info is then the only route left, so take them there rather than
        // leaving a dialog that cannot reappear.
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
            openAppInfo()
        finish()
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "could not open app info", it) }
    }

    private companion object {
        const val REQUEST_CODE = 1
        const val TAG = "MicPermission"
    }
}
