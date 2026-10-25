package com.pkx404.xparty

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.*

class WorldGlobeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
 private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
 private var land: List<List<FloatArray>> = emptyList()
 init {
  importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  val loader = Executors.newSingleThreadExecutor()
  loader.execute { try {
   val features = JSONObject(context.assets.open("world-countries.geojson").bufferedReader().use { it.readText() }).getJSONArray("features")
   val rings = mutableListOf<List<FloatArray>>()
   fun ring(points: JSONArray) { rings += (0 until points.length()).map { val p = points.getJSONArray(it); floatArrayOf(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) } }
   for (i in 0 until features.length()) { val geometry = features.getJSONObject(i).getJSONObject("geometry"); val coordinates = geometry.getJSONArray("coordinates"); if (geometry.getString("type") == "Polygon") for (j in 0 until coordinates.length()) ring(coordinates.getJSONArray(j)) else for (j in 0 until coordinates.length()) { val polygon = coordinates.getJSONArray(j); for (k in 0 until polygon.length()) ring(polygon.getJSONArray(k)) } }
   post { land = rings; invalidate() }
  } catch (_: Exception) {} finally { loader.shutdown() } }
 }
 override fun onDraw(canvas: Canvas) {
  canvas.drawColor(0xFF04060E.toInt()); paint.shader = null; paint.color = 0x0DFFFFFF; val d = resources.displayMetrics.density
  repeat(100) { i -> canvas.drawCircle(((i * 7919) % 997) / 997f * width, ((i * 3571) % 991) / 991f * height, .5f * d, paint) }
  val r = min(width * .46f, height * .3f); val x = width * .52f; val y = height * .3f
  paint.shader = RadialGradient(x, y, r + 40 * d, intArrayOf(0x663979CA, 0x003979CA), floatArrayOf(.65f, 1f), Shader.TileMode.CLAMP); canvas.drawCircle(x, y, r + 40 * d, paint)
  paint.shader = RadialGradient(x - r * .3f, y - r * .4f, r * 1.4f, intArrayOf(0xFF214965.toInt(), 0xFF040813.toInt()), null, Shader.TileMode.CLAMP); canvas.drawCircle(x, y, r, paint); paint.shader = null
  canvas.save(); val disk = Path().apply { addCircle(x, y, r, Path.Direction.CW) }; canvas.clipPath(disk)
  paint.color = 0xFF365B76.toInt(); val angle = 20.0 * PI / 180
  for (ring in land) { val path = Path(); var started = false; for (point in ring) {
   val lon = (point[0] - 77) * PI / 180; val lat = point[1] * PI / 180; val z = sin(angle) * sin(lat) + cos(angle) * cos(lat) * cos(lon)
   if (z < 0) { started = false; continue }
   val px = x + (cos(lat) * sin(lon) * r).toFloat(); val py = y + (-cos(angle) * sin(lat) + sin(angle) * cos(lat) * cos(lon)).toFloat() * r
   if (started) path.lineTo(px, py) else { path.moveTo(px, py); started = true }
  }; path.close(); canvas.drawPath(path, paint) }
  paint.shader = LinearGradient(x - r, y - r, x + r, y + r, intArrayOf(0x003C80C0, 0xCC000000.toInt()), null, Shader.TileMode.CLAMP); canvas.drawCircle(x, y, r, paint); paint.shader = null; canvas.restore()
 }
}
