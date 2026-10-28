package pl.dakil.appanalyser.ui.components.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import pl.dakil.appanalyser.R

/** Hands a file to whichever app handles its type; [onFailure] when there is none (or no URI). */
fun openFile(context: Context, uri: Uri?, mimeType: String, onFailure: () -> Unit) {
    if (uri == null) return onFailure()
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { onFailure() }
}

/** The system share sheet for a single file. */
fun shareFile(context: Context, uri: Uri?, mimeType: String) {
    if (uri == null) return
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.files_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
