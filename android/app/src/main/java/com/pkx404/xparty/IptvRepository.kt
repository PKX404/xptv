package com.pkx404.xparty

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IptvRepository {
 data class Country(val code: String, val name: String) { override fun toString() = name }
 private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
 private fun text(url: String, maximum: Long = 4L * 1024 * 1024): String {
  requireMediaUri(url)
  http.newCall(Request.Builder().url(url).build()).execute().use { response ->
   if (!response.isSuccessful) throw IOException("Directory unavailable (${response.code})")
   val source = response.body?.source() ?: throw IOException("Empty response")
   source.request(maximum + 1); if (source.buffer.size > maximum) throw IOException("Directory is too large")
   return source.readUtf8()
  }
 }
 suspend fun countries(): List<Country> = withContext(Dispatchers.IO) {
  val values = JSONArray(text("https://iptv-org.github.io/api/countries.json"))
  (0 until values.length()).map { values.getJSONObject(it) }.filter { it.optString("code").matches(Regex("[A-Z]{2}")) }.map { Country(it.getString("code"), it.getString("name")) }.sortedBy { it.name }
 }
 suspend fun channels(country: String): List<IptvChannel> = withContext(Dispatchers.IO) {
  require(country.matches(Regex("[A-Z]{2}")))
  parsePlaylist(text("https://iptv-org.github.io/iptv/countries/${country.lowercase()}.m3u"), country)
 }
 suspend fun radios(country: String): List<IptvChannel> = withContext(Dispatchers.IO) {
  require(country.matches(Regex("[A-Z]{2}")))
  val items = JSONArray(text("https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/$country?hidebroken=true&limit=200&order=clickcount&reverse=true"))
  (0 until items.length()).map { items.getJSONObject(it) }.mapNotNull { value ->
   val url = value.optString("url_resolved"); if (runCatching { requireMediaUri(url) }.isFailure) null
   else IptvChannel(value.optString("stationuuid"), value.optString("name"), url, "Radio", country, value.optString("favicon").takeIf { it.startsWith("https://") }, true)
  }
 }
 fun parsePlaylist(playlist: String, country: String): List<IptvChannel> {
  require(playlist.trimStart().startsWith("#EXTM3U"))
  val channels = mutableListOf<IptvChannel>(); var name = ""; var id = ""; var group = "Entertainment"; var logo: String? = null; var blocked = false
  for (line in playlist.lineSequence().map { it.trim() }) {
   if (line.startsWith("#EXTINF:")) {
    fun attr(key: String) = Regex("$key=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
    name = line.substringAfterLast(',').take(150); id = attr("tvg-id").orEmpty(); group = attr("group-title")?.substringBefore(';') ?: "Entertainment"; logo = attr("tvg-logo")?.takeIf { it.startsWith("https://") }; blocked = false
   } else if (line.startsWith("#EXTVLCOPT") || line.startsWith("#KODIPROP") || line.startsWith("#EXTHTTP")) blocked = true
   else if (line.isNotBlank() && !line.startsWith("#") && !blocked && runCatching { requireMediaUri(line) }.isSuccess) {
    channels += IptvChannel(id.ifBlank { line }, name.ifBlank { "Live channel" }, line, group, country, logo); if (channels.size >= 1500) break
   }
  }
  return channels.distinctBy { it.url }
 }
 // Optional XMLTV guide. No fabricated programmes or viewer counts when absent.
 suspend fun nowPlaying(epgUrl: String): Map<String, String> = withContext(Dispatchers.IO) {
  val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser(); parser.setInput(text(epgUrl).reader())
  val format = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US).apply { isLenient = false }
  val programmes = mutableMapOf<String, String>(); var channel: String? = null; val now = System.currentTimeMillis()
  while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
   if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "programme") {
    val start = runCatching { format.parse(parser.getAttributeValue(null, "start"))?.time }.getOrNull()
    val stop = runCatching { format.parse(parser.getAttributeValue(null, "stop"))?.time }.getOrNull()
    channel = if (start != null && stop != null && now in start until stop) parser.getAttributeValue(null, "channel") else null
   } else if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "title" && channel != null) programmes[channel] = parser.nextText().take(200)
   else if (parser.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "programme") channel = null
   parser.next()
  }
  programmes
 }
}
