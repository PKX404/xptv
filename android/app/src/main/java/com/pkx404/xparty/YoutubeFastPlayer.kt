package com.pkx404.xparty

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.*

class YoutubeFastPlayer(context: Context) : AutoCloseable {
 val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
 private val app = context.applicationContext
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 private val provider = YoutubeNewPipeProvider()
 private var loading: Job? = null
 private var generation = 0L
 private var currentUrl: String? = null
 private var retried = false
 var onError: ((String) -> Unit)? = null
 init { player.addListener(object : Player.Listener {
  override fun onPlayerError(error: PlaybackException) {
   val url = currentUrl
   if (url != null && !retried && error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
    retried = true; val position = player.currentPosition; val play = player.playWhenReady
    scope.launch { PlaybackCache.invalidate(provider, url); openInternal(url, play, position) }
   } else onError?.invoke("This source could not play. Retry or choose another video.")
  }
 }) }
 fun prefetch(url: String) { scope.launch(Dispatchers.IO) { runCatching { PlaybackCache.resolve(provider, url) } } }
 fun open(url: String, play: Boolean = true) { retried = false; openInternal(url, play, 0) }
 fun openDirect(url: String, headers: Map<String, String> = emptyMap()) { stop(); setInfo(RavePlaybackInfo(url, headers = headers), false) }
 private fun openInternal(url: String, play: Boolean, position: Long) {
  val revision = ++generation; currentUrl = url; loading?.cancel(); player.stop(); player.clearMediaItems()
  loading = scope.launch {
   try { val info = PlaybackCache.resolve(provider, url); ensureActive(); if (revision != generation) return@launch; setInfo(info, play, position) }
   catch (error: CancellationException) { throw error }
   catch (_: Exception) { if (revision == generation) onError?.invoke("YouTube extraction is unavailable. Retry or choose another video.") }
  }
 }
 fun setInfo(info: RavePlaybackInfo, play: Boolean = true, position: Long = 0) {
  val main = source(info.url, info.mimeType, info)
  val merged = info.audioUrl?.let { MergingMediaSource(true, main, source(it, info.audioMimeType, info)) } ?: main
  player.setMediaSource(merged, position); player.prepare(); player.playWhenReady = play
 }
 private fun source(url: String, mime: String?, info: RavePlaybackInfo): MediaSource {
  val uri = requireMediaUri(url)
  val requestHeaders = info.headers.filterKeys { it.lowercase() !in setOf("host", "content-length", "connection", "cookie") }.toMutableMap()
  // Cookies are scoped to the resolved video origin, never copied to an audio CDN.
  if (Uri.parse(info.url).host == uri.host) info.cookies?.let { requestHeaders["Cookie"] = it }
  else requestHeaders.keys.removeAll { it.equals("Authorization", true) }
  val http = DefaultHttpDataSource.Factory().setUserAgent("Xparty/1.2").setConnectTimeoutMs(8000).setReadTimeoutMs(15000).setDefaultRequestProperties(requestHeaders)
  val factory: DataSource.Factory = DefaultDataSource.Factory(app, http)
  val item = MediaItem.Builder().setUri(uri).setMimeType(mime)
  info.drm?.let { drm -> item.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(drm.scheme).setLicenseUri(requireMediaUri(drm.licenseUrl)).setLicenseRequestHeaders(drm.headers).build()) }
  return DefaultMediaSourceFactory(factory).createMediaSource(item.build())
 }
 fun stop() { generation++; loading?.cancel(); loading = null; currentUrl = null; player.stop(); player.clearMediaItems() }
 override fun close() { stop(); scope.cancel(); player.release() }
}
