package com.pkx404.xparty

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.video.VideoSize
import org.json.JSONObject

/** Only a private port delivered to the trusted top document can control playback. */
class NativeVideoEngine(private val activity: Activity, private val origin: String) : NativeVideoRenderer.Listener {
 @JvmField val view = NativeVideoRenderer(activity, this)
 private val handler = Handler(Looper.getMainLooper())
 private val fast = YoutubeFastPlayer(activity)
 private val player get() = fast.player
 private var port: WebMessagePort? = null
 private var sourceId = ""
 private var prepared = false
 private var active = false
 private var visible = true
 private var suspended = false
 private var desiredPlaying = false
 private var destroyed = false
 private var volume = .7f
 private val ambient = EmbeddedAmbientSampler(activity) { message -> post(message) }
 private val tickerTask = object : Runnable { override fun run() { if (destroyed) return; if (prepared && !suspended) state("state"); handler.postDelayed(this, 250) } }
 init {
  fast.onError = { fail(it) }
  player.addListener(object : Player.Listener {
   override fun onPlaybackStateChanged(value: Int) {
    if (sourceId.isEmpty()) return
    when (value) {
     Player.STATE_READY -> { val first = !prepared; prepared = true; player.volume = volume; view.setVideoVisible(visible); state(if (first) "ready" else "buffered") }
     Player.STATE_BUFFERING -> state("buffering")
     Player.STATE_ENDED -> { desiredPlaying = false; state("ended") }
    }
   }
   override fun onVideoSizeChanged(size: VideoSize) { view.setVideoSize(size.width, size.height) }
  })
  handler.post(tickerTask)
 }
 fun connect(web: WebView) {
  disconnect(); if (destroyed) return; ambient.attach(web)
  val ports = web.createWebMessageChannel(); port = ports[0]
  ports[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
   override fun onMessage(ignored: WebMessagePort, message: WebMessage) {
    val data = message.data ?: return; if (data.length > 12000) return
    try { command(JSONObject(data)) } catch (_: Exception) { state("error", "Native video command could not be read") }
   }
  }, handler)
  web.postWebMessage(WebMessage("xparty-native-video-v1", arrayOf(ports[1])), Uri.parse(origin))
 }
 private fun command(m: JSONObject) {
  when (m.optString("type")) {
   "hello" -> { state("available"); return }
   "stop" -> { stop(); return }
   "ambient-watch" -> { ambient.configure(m); return }
   "fullscreen" -> { fullscreen(m.optBoolean("value")); return }
   "prefetch" -> { val url = m.optString("url"); if (url.matches(Regex("https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}"))) fast.prefetch(url); return }
   "load" -> { load(m); return }
  }
  if (m.optString("sourceId") != sourceId) return
  when (m.optString("type")) {
   "layout" -> {
    val values = listOf("viewport", "x", "y", "width", "height").map { m.optDouble(it).toFloat() }
    if (values.all { it.isFinite() } && values[0] > 0 && values[3] > 0 && values[4] > 0) view.setBox(values[0], values[1], values[2], values[3], values[4])
   }
   "visible" -> { visible = m.optBoolean("value"); view.setVideoVisible(visible && active) }
   "volume" -> { val value = m.optDouble("value"); if (value.isFinite()) { volume = value.coerceIn(0.0, 1.0).toFloat(); player.volume = volume } }
   "playback" -> if (prepared) {
    val position = m.optDouble("position"); if (position.isFinite() && player.isCurrentMediaItemSeekable) player.seekTo((position.coerceAtLeast(0.0) * 1000).toLong().coerceAtMost(if (player.duration > 0) player.duration else Long.MAX_VALUE))
    desiredPlaying = m.optBoolean("playing"); player.playWhenReady = desiredPlaying && !suspended; state("state")
   }
  }
 }
 private fun load(m: JSONObject) {
  val id = m.optString("sourceId"); require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
  val url = m.optString("url"); val uri = requireMediaUri(url); require(url.length <= 4096)
  val kind = m.optString("kind"); require(kind in setOf("file", "direct", "iptv", "youtube"))
  val headers = mutableMapOf<String, String>()
  if (kind == "file") {
   require(url == "$origin/api/file/$id")
   val token = m.optString("token"); require(token.isNotBlank() && token.length <= 4096 && !token.contains('\n') && !token.contains('\r')); headers["Authorization"] = "Bearer $token"
  }
  if (kind == "youtube") require(uri.host == "www.youtube.com" && url.matches(Regex("https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}")))
  stop(); sourceId = id; visible = true
  if (kind == "youtube") fast.open(url, false) else fast.openDirect(url, headers)
 }
 private fun post(message: JSONObject) { if (!destroyed) runCatching { port?.postMessage(WebMessage(message.toString())) } }
 private fun state(type: String, error: String? = null) {
  val data = JSONObject().put("type", type).put("sourceId", sourceId).put("active", active).put("ready", prepared)
  if (type == "available") data.put("ambientSupported", true).put("fastYoutube", true)
  if (prepared) data.put("position", player.currentPosition / 1000.0).put("duration", if (player.duration == C.TIME_UNSET) 0 else player.duration / 1000.0).put("playing", player.isPlaying)
  error?.let { data.put("message", it) }; post(data)
 }
 override fun surfaceReady(surface: Surface) { if (!destroyed) player.setVideoSurface(surface) }
 override fun frameReady() { if (prepared) { active = true; view.setVideoVisible(visible); state("frame") } }
 override fun failed(message: String) { fail(message) }
 private fun fail(message: String) { prepared = false; active = false; desiredPlaying = false; fast.stop(); view.clearVideo(); state("error", message) }
 private fun fullscreen(enabled: Boolean) {
  if (enabled) { activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY }
  else { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
 }
 fun suspend() { suspended = true; ambient.suspend(); player.pause(); view.onPause() }
 fun resume() { view.onResume(); suspended = false; ambient.resume(); if (prepared && desiredPlaying) player.play() }
 private fun stop() { ambient.stop(); sourceId = ""; prepared = false; active = false; desiredPlaying = false; fast.stop(); view.clearVideo() }
 fun disconnect() { stop(); fullscreen(false); port?.close(); port = null }
 fun destroy() { disconnect(); destroyed = true; handler.removeCallbacksAndMessages(null); ambient.destroy(); fast.close(); view.release() }
}
