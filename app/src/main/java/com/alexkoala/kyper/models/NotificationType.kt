package com.alexkoala.kyper.models

import androidx.annotation.StringRes
import com.alexkoala.kyper.R

enum class NotificationType(@StringRes val labelRes: Int, val isConfigurable: Boolean = true) {
    STANDARD(R.string.type_standard),
    MESSAGE(R.string.type_message),
    PROGRESS(R.string.type_progress),
    DOWNLOAD(R.string.type_download),
    MEDIA(R.string.type_media),
    NAVIGATION(R.string.type_nav),
    CALL(R.string.type_call),
    TIMER(R.string.type_timer),
    SCREEN_RECORDING(R.string.type_screen_recording, isConfigurable = false);

    companion object {
        val configurableEntries: List<NotificationType>
            get() = entries.filter { it.isConfigurable }
    }
}
