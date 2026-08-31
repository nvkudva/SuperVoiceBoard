// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. New file: the voice models screen — download, cancel, remove,
// and import a file the user already has.
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vboard.app.models.ModelDownloadService
import com.vboard.app.voice.VoiceRuntime
import com.vboard.app.voice.voiceRuntimeOrNull
import com.vboard.core.model.ByteSize
import com.vboard.core.model.DownloadDecision
import com.vboard.core.model.DownloadPolicy
import com.vboard.core.model.InstallError
import com.vboard.core.model.ModelCatalog
import com.vboard.core.model.ModelPack
import com.vboard.core.model.PackInstaller
import com.vboard.core.model.PackState
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row per model pack, with whatever action that pack's state allows.
 *
 * Dictation needs several hundred megabytes of model, which is why this is a
 * screen rather than a switch: the user decides what to spend, when, and can get
 * it back. Import exists because someone on a metered connection or an
 * air-gapped device should not have to pull the same archive twice.
 */
@Composable
fun VoiceModelsScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    val runtime = remember { voiceRuntimeOrNull(context) }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_voice_models),
        settings = emptyList(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (runtime == null) {
                Text(
                    text = stringResource(R.string.voice_models_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            val scheduled by ModelDownloadService.observeScheduledWork(context)
                .collectAsState(initial = emptyList())
            val liveStates by ModelDownloadService.states.collectAsState()
            for (pack in ModelCatalog.packs) {
                PackRow(
                    pack = pack,
                    runtime = runtime,
                    liveState = liveStates[pack.id],
                    queued = scheduled.any { it.packId == pack.id && it.waitingForNetwork },
                    running = scheduled.any { it.packId == pack.id },
                )
            }
            Text(
                text = stringResource(R.string.voice_models_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun PackRow(
    pack: ModelPack,
    runtime: VoiceRuntime,
    liveState: PackState?,
    queued: Boolean,
    running: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Disk is the durable answer; the live flow is empty after process death.
    var diskState by remember(pack.id) { mutableStateOf<PackState>(PackState.NotInstalled) }
    var message by remember(pack.id) { mutableStateOf<String?>(null) }
    // Set when the link is metered and the user has not agreed to spend data on
    // this download; the dialog names the real size before anything is enqueued.
    var confirmMeteredBytes by remember(pack.id) { mutableStateOf<Long?>(null) }

    /**
     * Never starts a download on cellular without asking. DownloadPolicy owns
     * that rule so it cannot be lost in a Compose callback.
     */
    fun requestDownload(meteredConsent: Boolean) {
        when (val decision = DownloadPolicy.decide(
            network = ModelDownloadService.networkState(context),
            meteredConsent = meteredConsent,
            bytes = pack.totalBytes,
        )) {
            is DownloadDecision.ConfirmMetered -> confirmMeteredBytes = decision.bytes
            is DownloadDecision.Enqueue -> {
                if (decision.allowMetered) ModelDownloadService.startAllowingMetered(context, pack.id)
                else ModelDownloadService.start(context, pack.id)
            }
        }
    }
    LaunchedEffect(pack.id, liveState) {
        diskState = withContext(Dispatchers.IO) { runtime.packInstaller.stateOf(pack) }
    }
    // Disk wins once nothing is scheduled: the worker's last published state is
    // what it *did*, and a pack it installed into a directory this process cannot
    // read must not keep reading "Installed" here while the mic says otherwise.
    val state = if (running || queued) liveState ?: diskState else diskState

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = context.getString(R.string.voice_models_importing)
            val result = importInto(context, runtime, pack, uri)
            message = context.getString(
                when (result) {
                    PackInstaller.ImportResult.STAGED -> R.string.voice_models_import_ok
                    PackInstaller.ImportResult.DIGEST_MISMATCH -> R.string.voice_models_import_wrong_file
                    PackInstaller.ImportResult.UNKNOWN_FILE -> R.string.voice_models_import_unknown_file
                    PackInstaller.ImportResult.ALREADY_INSTALLED -> R.string.voice_models_import_installed
                    PackInstaller.ImportResult.IO_ERROR -> R.string.voice_models_import_io
                },
            )
            if (result == PackInstaller.ImportResult.STAGED) {
                // Staged bytes still have to be extracted and finalized, which is
                // exactly what a download's last stage does — so run that. It is
                // enqueued without a network constraint on purpose: the files are
                // already here, so asking about mobile data would be asking about
                // bytes nobody is going to fetch.
                ModelDownloadService.startAllowingMetered(context, pack.id)
            }
            diskState = withContext(Dispatchers.IO) { runtime.packInstaller.stateOf(pack) }
        }
    }

    confirmMeteredBytes?.let { bytes ->
        ConfirmationDialog(
            onDismissRequest = { confirmMeteredBytes = null },
            onConfirmed = {
                confirmMeteredBytes = null
                requestDownload(meteredConsent = true)
            },
            confirmButtonText = stringResource(R.string.voice_models_use_mobile_data),
            title = { Text(stringResource(R.string.voice_models_metered_title)) },
            content = {
                Text(stringResource(R.string.voice_models_metered_message, ByteSize.format(bytes)))
            },
        )
    }
    PreferenceGroup {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // the pack's own state, so a glance down the list reads as a checklist
                Icon(
                    painterResource(
                        if (state is PackState.Installed) R.drawable.ic_setup_check
                        else R.drawable.ic_settings_voice
                    ),
                    null,
                    Modifier.size(20.dp),
                    tint = if (state is PackState.Installed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    pack.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = describe(context, pack, state, queued),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state is PackState.Downloading) {
                LinearProgressIndicator(
                    progress = { state.fraction.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    running || state is PackState.Downloading || state is PackState.Verifying ->
                        TextButton(onClick = { ModelDownloadService.cancel(context, pack.id) }) {
                            Text(stringResource(R.string.voice_models_cancel))
                        }
                    state is PackState.Installed ->
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { runtime.packInstaller.delete(pack) }
                                diskState = PackState.NotInstalled
                                message = null
                            }
                        }) { Text(stringResource(R.string.voice_models_remove)) }
                    else -> {
                        // downloading is the expected action; importing is the escape hatch
                        FilledTonalButton(
                            onClick = { requestDownload(meteredConsent = false) },
                            shape = MaterialTheme.shapes.large,
                        ) { Text(stringResource(R.string.voice_models_download)) }
                        TextButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.voice_models_import))
                        }
                    }
                }
            }
        }
    }
}

/** Streams the picked document into the installer's staging area. */
private suspend fun importInto(
    context: Context,
    runtime: VoiceRuntime,
    pack: ModelPack,
    uri: Uri,
): PackInstaller.ImportResult = withContext(Dispatchers.IO) {
    val name = displayName(context, uri) ?: return@withContext PackInstaller.ImportResult.UNKNOWN_FILE
    runCatching {
        runtime.packInstaller.importFile(pack, name) {
            context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("could not open $name")
        }
    }.getOrElse { PackInstaller.ImportResult.IO_ERROR }
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

private fun describe(context: Context, pack: ModelPack, state: PackState, queued: Boolean): String {
    val size = ByteSize.format(pack.totalBytes)
    return when {
        queued && state !is PackState.Downloading -> context.getString(R.string.voice_models_queued)
        state is PackState.Downloading -> context.getString(
            R.string.voice_models_downloading,
            (state.fraction * 100).toInt(),
            size,
        )
        state is PackState.Verifying -> context.getString(R.string.voice_models_verifying)
        state is PackState.Installed -> context.getString(R.string.voice_models_installed, size)
        state is PackState.Failed -> context.getString(
            when (state.error) {
                InstallError.NETWORK -> R.string.voice_models_failed_network
                InstallError.CHECKSUM_MISMATCH -> R.string.voice_models_failed_checksum
                InstallError.INSUFFICIENT_STORAGE -> R.string.voice_models_failed_storage
                InstallError.CANCELLED -> R.string.voice_models_failed_cancelled
                InstallError.IO -> R.string.voice_models_failed_io
            },
        )
        pack.required -> context.getString(R.string.voice_models_needed, size)
        else -> context.getString(R.string.voice_models_optional, size)
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            VoiceModelsScreen { }
        }
    }
}
