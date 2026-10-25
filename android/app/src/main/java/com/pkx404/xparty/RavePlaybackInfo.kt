package com.pkx404.xparty

import android.net.Uri
import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// The name identifies XParty's playback contract, not a recovered Rave API.
class RavePlaybackInfo(
 val url: String,
 val audioUrl: String? = null,
 val mimeType: String? = null,
 val audioMimeType: String? = null,
 val headers: Map<String, String> = emptyMap(),
 val cookies: String? = null,
 val title: String = "",
 val live: Boolean = false,
 val expiresAtMs: Long = System.currentTimeMillis() + 600_000,
 val drm: Drm? = null
) {
 class Drm(val scheme: UUID, val licenseUrl: String, val headers: Map<String, String> = emptyMap())
 override fun toString() = "RavePlaybackInfo(live=$live, protected=${drm != null})"
}

interface FastOpenProvider { suspend fun extract(url: String): RavePlaybackInfo }

object PlaybackCache {
 private data class Entry(val info: RavePlaybackInfo, val until: Long)
 private val entries = LinkedHashMap<String, Entry>(24, .75f, true)
 private val lock = Mutex()
 suspend fun resolve(provider: FastOpenProvider, url: String): RavePlaybackInfo = lock.withLock {
  val key = provider.javaClass.name + ":" + url
  entries[key]?.takeIf { it.until > SystemClock.elapsedRealtime() && it.info.expiresAtMs > System.currentTimeMillis() + 30_000 }?.let { return@withLock it.info }
  val info = withContext(Dispatchers.IO) { provider.extract(url) }
  val ttl = minOf(600_000L, info.expiresAtMs - System.currentTimeMillis() - 30_000)
  if (ttl > 0) { entries[key] = Entry(info, SystemClock.elapsedRealtime() + ttl); while (entries.size > 24) entries.remove(entries.keys.first()) }
  info
 }
 suspend fun invalidate(provider: FastOpenProvider, url: String) = lock.withLock { entries.remove(provider.javaClass.name + ":" + url); Unit }
}

internal fun requireMediaUri(value: String): Uri = Uri.parse(value).also {
 require(it.scheme == "https" && it.userInfo == null && !it.host.isNullOrBlank()) { "Use a secure media address" }
 require(it.host != "localhost" && !it.host!!.endsWith(".local") && !it.host!!.matches(Regex("[0-9.:]+"))) { "Private addresses are not supported" }
}
