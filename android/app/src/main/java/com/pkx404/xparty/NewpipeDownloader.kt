package com.pkx404.xparty

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewpipeDownloader : Downloader() {
 private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
 override fun execute(request: Request): Response {
  val builder = okhttp3.Request.Builder().url(request.url()).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
  request.headers().forEach { (name, values) -> builder.removeHeader(name); values.forEach { builder.addHeader(name, it) } }
  val data = request.dataToSend()
  val body = data?.toRequestBody() ?: if (request.httpMethod() in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
  client.newCall(builder.method(request.httpMethod(), body).build()).execute().use { response ->
   if (response.code == 429) throw ReCaptchaException("YouTube requires a challenge; try again later", request.url())
   val output = ByteArrayOutputStream()
   response.body?.byteStream()?.use { input ->
    val buffer = ByteArray(8192)
    while (true) { val read = input.read(buffer); if (read < 0) break; if (output.size() + read > 12 * 1024 * 1024) throw IOException("Extractor response too large"); output.write(buffer, 0, read) }
   }
   return Response(response.code, response.message, response.headers.toMultimap(), output.toString("UTF-8"), response.request.url.toString())
  }
 }
 companion object {
  @Volatile private var initialized = false
  @Synchronized fun initialize() { if (!initialized) { NewPipe.init(NewpipeDownloader()); initialized = true } }
 }
}

class YoutubeNewPipeProvider : FastOpenProvider {
 override suspend fun extract(url: String): RavePlaybackInfo {
  val uri = requireMediaUri(url)
  require(uri.host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")) { "Use a YouTube video link" }
  NewpipeDownloader.initialize()
  val extractor = ServiceList.YouTube.getStreamExtractor(url)
  extractor.fetchPage()
  val hls = runCatching { extractor.hlsUrl }.getOrDefault("")
  if (hls.isNotBlank()) return RavePlaybackInfo(hls, mimeType = "application/x-mpegURL", title = extractor.name, live = true, expiresAtMs = expiry(hls))
  val dash = runCatching { extractor.dashMpdUrl }.getOrDefault("")
  if (dash.isNotBlank()) return RavePlaybackInfo(dash, mimeType = "application/dash+xml", title = extractor.name, expiresAtMs = expiry(dash))
  fun choose(values: List<VideoStream>) = values.filter { it.isUrl && it.content.startsWith("https://") }.sortedWith(compareByDescending<VideoStream> { it.format?.mimeType == "video/mp4" }.thenByDescending { it.height in 1..1080 }.thenByDescending { if (it.height <= 1080) it.height else -it.height }).firstOrNull()
  val video = choose(extractor.videoOnlyStreams)
  val audio = extractor.audioStreams.filter { it.isUrl && it.content.startsWith("https://") }.sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.mimeType == "audio/mp4" }.thenByDescending { it.averageBitrate }).firstOrNull()
  if (video != null && audio != null) return RavePlaybackInfo(video.content, audio.content, video.format?.mimeType, audio.format?.mimeType, title = extractor.name, expiresAtMs = minOf(expiry(video.content), expiry(audio.content)))
  val combined = choose(extractor.videoStreams) ?: throw IOException("No directly playable YouTube stream is available")
  return RavePlaybackInfo(combined.content, mimeType = combined.format?.mimeType, title = extractor.name, expiresAtMs = expiry(combined.content))
 }
 private fun expiry(url: String) = android.net.Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()?.times(1000) ?: (System.currentTimeMillis() + 600_000)
}
