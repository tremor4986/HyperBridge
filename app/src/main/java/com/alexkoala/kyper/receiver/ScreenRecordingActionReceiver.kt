package com.alexkoala.kyper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alexkoala.kyper.service.diagnostics.DiagnosticsStore
import com.alexkoala.kyper.service.recording.ScreenRecordingClassifier
import com.alexkoala.kyper.service.recording.XiaomiScreenRecordingControlBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenRecordingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = XiaomiScreenRecordingControlBackend(context).stop()
                DiagnosticsStore.record(
                    classification = "SCREEN_RECORDING",
                    action = if (result.isSuccess) "stop-sent" else "stop-failed",
                    packageName = ScreenRecordingClassifier.PACKAGE_NAME,
                    reason = result.exceptionOrNull()?.javaClass?.simpleName
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.alexkoala.kyper.action.STOP_SCREEN_RECORDING"
    }
}
