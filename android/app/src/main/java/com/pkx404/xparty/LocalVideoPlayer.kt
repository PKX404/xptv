package com.pkx404.xparty

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageButton
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource

class LocalVideoPlayer(private val context: Context, val player: ExoPlayer, closeButton: ImageButton, private val onClose: () -> Unit) {
 companion object {
  const val MAX_BYTES = 5L * 1024 * 1024 * 1024
  fun picker() = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
 }
 init { closeButton.visibility = View.VISIBLE; closeButton.setOnClickListener { close() } }
 fun open(uri: Uri, grantedFlags: Int) {
  require(uri.scheme == "content") { "Choose a video using the file picker" }
  val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
  require(size == null || size in 1..MAX_BYTES) { "Choose a video up to 5 GB" }
  if (grantedFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
  // ContentDataSource streams the SAF descriptor; no File URI conversion or RAM copy.
  player.setMediaSource(ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(uri)))
  player.prepare(); player.playWhenReady = true
 }
 fun close() { player.stop(); player.clearMediaItems(); onClose() }
}
