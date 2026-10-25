package com.pkx404.xparty

import android.app.Activity
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.view.*
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.*

class NativeSourceSheet(private val activity: Activity, private val listener: Listener) : AutoCloseable {
 interface Listener { fun select(url: String); fun provider(name: String); fun pickLocal(); fun control(name: String, value: Int) }
 private val context = ContextThemeWrapper(activity, R.style.XpartyMaterial)
 private val dialog = BottomSheetDialog(context)
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 private val vm = QuickSearchVM()
 private val fast = YoutubeFastPlayer(activity)
 private val repository = IptvRepository()
 private var renderJob: Job? = null
 private var countryJob: Job? = null
 private var surface: Surface? = null
 private var immersive: ImmersiveBgRenderer? = null
 private var local: LocalVideoPlayer? = null
 private var released = false
 init {
  dialog.setOnDismissListener { close() }
  dialog.setOnShowListener {
   dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED; dialog.behavior.skipCollapsed = true
   dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); dialog.window?.setDimAmount(.6f)
   if (Build.VERSION.SDK_INT >= 31) dialog.window?.attributes = dialog.window?.attributes?.apply { flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND; blurBehindRadius = (20 * activity.resources.displayMetrics.density).toInt() }
  }
  fast.onError = { Toast.makeText(activity, it, Toast.LENGTH_LONG).show() }
 }
 fun show() { showQuick(); dialog.show() }
 private fun page(layout: Int): View {
  renderJob?.cancel(); countryJob?.cancel(); fast.stop(); surface?.let { fast.player.clearVideoSurface(it); it.release() }; surface = null; immersive?.close(); immersive = null
  val view = LayoutInflater.from(context).inflate(layout, null)
  dialog.setContentView(view); view.layoutParams = view.layoutParams.apply { height = (activity.resources.displayMetrics.heightPixels * .9).toInt() }
  return view
 }
 fun showQuick() {
  val view = page(R.layout.quick_search_page)
  val adapter = QuickSearchAdapter({ listener.select(it.url); dialog.dismiss() }, { fast.prefetch(it.url) })
  view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.quick_results).apply { layoutManager = GridLayoutManager(context, 2); this.adapter = adapter }
  view.findViewById<EditText>(R.id.quick_query).addTextChangedListener { vm.search(it.toString()) }
  view.findViewById<View>(R.id.pick_local).setOnClickListener { listener.pickLocal() }
  view.findViewById<View>(R.id.full_sources).setOnClickListener { showSources() }
  renderJob = scope.launch { vm.state.collect { state -> adapter.submitList(state.videos); view.findViewById<TextView>(R.id.quick_status).text = state.error ?: if (state.loading) "Searching…" else if (state.query.isNotEmpty()) "${state.videos.size} videos" else "Search YouTube or choose a local video" } }
 }
 fun showSources() {
  val view = page(R.layout.source_grid_page)
  val ids = mapOf(R.id.source_iptv to "iptv", R.id.source_youtube to "youtube", R.id.source_netflix to "netflix", R.id.source_twitch to "twitch", R.id.source_prime to "prime", R.id.source_soundcloud to "soundcloud", R.id.source_vimeo to "vimeo", R.id.source_drive to "drive", R.id.source_direct to "direct", R.id.source_file to "file")
  ids.forEach { (id, key) -> view.findViewById<View>(id).setOnClickListener { when (key) { "iptv" -> showIptv(); "youtube" -> showQuick(); "file" -> listener.pickLocal(); else -> { listener.provider(key); dialog.dismiss() } } } }
  view.findViewById<View>(R.id.source_iptv).isSelected = true
  view.findViewById<View>(R.id.source_local_file).setOnClickListener { listener.pickLocal() }
  view.findViewById<View>(R.id.source_open_stream).setOnClickListener { val value = view.findViewById<EditText>(R.id.source_stream_url).text.toString().trim(); if (runCatching { requireMediaUri(value) }.isSuccess) { listener.select(value); dialog.dismiss() } else Toast.makeText(context, "Use a secure video or stream link", Toast.LENGTH_SHORT).show() }
  fun slider(id: Int, key: String) { view.findViewById<SeekBar>(id).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(v: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) listener.control(key, value) }; override fun onStartTrackingTouch(v: SeekBar) {}; override fun onStopTrackingTouch(v: SeekBar) {} }) }
  slider(R.id.source_call_volume, "call-volume"); slider(R.id.source_playback_volume, "movie-volume")
  view.findViewById<Switch>(R.id.source_up_next).setOnCheckedChangeListener { _, checked -> listener.control("auto-advance", if (checked) 1 else 0) }
  view.findViewById<Switch>(R.id.source_integrity_skip).setOnCheckedChangeListener { _, checked -> listener.control("skip-voting", if (checked) 1 else 0) }
  val list = androidx.recyclerview.widget.RecyclerView(context).apply { layoutManager = GridLayoutManager(context, 2) }
  view.findViewById<FrameLayout>(R.id.source_channel_list).addView(list, FrameLayout.LayoutParams(-1, -1))
  bindDirectory(view.findViewById(R.id.source_country), view.findViewById(R.id.source_channel_query), list, view.findViewById(R.id.source_channel_count), null)
 }
 fun showIptv() {
  val view = page(R.layout.iptv_page)
  bindDirectory(view.findViewById(R.id.iptv_country), view.findViewById(R.id.iptv_query), view.findViewById(R.id.iptv_results), view.findViewById(R.id.iptv_status), view.findViewById(R.id.iptv_categories))
 }
 private fun bindDirectory(spinner: Spinner, search: EditText, list: androidx.recyclerview.widget.RecyclerView, status: TextView, chips: ChipGroup?) {
  val adapter = IptvCategoryAdapter { listener.select(it.url); dialog.dismiss() }; list.layoutManager = GridLayoutManager(context, 2); list.adapter = adapter
  var category = "All"; var selected: IptvRepository.Country? = null
  fun load() { val country = selected ?: return; countryJob?.cancel(); countryJob = scope.launch { status.text = "Loading channels…"; try { val items = if (category == "Radio") repository.radios(country.code) else repository.channels(country.code); ensureActive(); adapter.replace(items); adapter.filter(category, search.text.toString()); status.text = "${items.size} ${if (category == "Radio") "stations" else "channels"}" } catch (e: CancellationException) { throw e } catch (_: Exception) { status.text = "Directory unavailable. Choose another country or retry." } } }
  search.addTextChangedListener { adapter.filter(category, it.toString()) }
  chips?.setOnCheckedStateChangeListener { group, checked -> val next = checked.firstOrNull()?.let { group.findViewById<Chip>(it).text.toString() } ?: "All"; val reload = (category == "Radio") != (next == "Radio"); category = next; if (reload) load() else adapter.filter(category, search.text.toString()) }
  renderJob = scope.launch { try { val countries = repository.countries(); spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, countries); spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) {}; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selected = countries[position]; load() } }; spinner.setSelection(countries.indexOfFirst { it.code == "IN" }.coerceAtLeast(0)) } catch (e: CancellationException) { throw e } catch (_: Exception) { status.text = "Country directory unavailable" } }
 }
 fun playLocal(uri: Uri, flags: Int) {
  val view = page(R.layout.rave_room)
  val renderer = view.findViewById<ImmersiveBgRenderer>(R.id.room_immersive); immersive = renderer; renderer.foreground().visibility = View.GONE
  val texture = view.findViewById<TextureView>(R.id.native_video_surface)
  renderer.setFrameSource(ImmersiveBgRenderer.textureFrames(texture))
  texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
   override fun onSurfaceTextureAvailable(value: SurfaceTexture, width: Int, height: Int) { surface = Surface(value); fast.player.setVideoSurface(surface) }
   override fun onSurfaceTextureSizeChanged(value: SurfaceTexture, width: Int, height: Int) {}
   override fun onSurfaceTextureDestroyed(value: SurfaceTexture): Boolean { surface?.let { fast.player.clearVideoSurface(it); it.release() }; surface = null; return true }
   override fun onSurfaceTextureUpdated(value: SurfaceTexture) {}
  }
  local = LocalVideoPlayer(activity, fast.player, view.findViewById(R.id.local_close)) { showQuick() }
  view.findViewById<View>(R.id.native_play).setOnClickListener { if (fast.player.isPlaying) fast.player.pause() else fast.player.play() }
  view.findViewById<View>(R.id.native_search).setOnClickListener { showQuick() }
  view.findViewById<View>(R.id.native_fullscreen).setOnClickListener { val panel = view.findViewById<View>(R.id.native_channel_panel); panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
  view.findViewById<View>(R.id.native_invite).setOnClickListener { listener.control("copy", 1) }
  view.findViewById<View>(R.id.native_people).setOnClickListener { listener.control("people-button", 1); dialog.dismiss() }
  view.findViewById<VerticalVolumeSlider>(R.id.native_playback_volume).onChanged = { fast.player.volume = it / 100f }
  view.findViewById<VerticalVolumeSlider>(R.id.native_call_volume).apply { value = 100; onChanged = { listener.control("call-volume", it) } }
  val seek = view.findViewById<SeekBar>(R.id.native_seek)
  seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser && fast.player.duration > 0) fast.player.seekTo(fast.player.duration * value / 1000) }; override fun onStartTrackingTouch(bar: SeekBar) {}; override fun onStopTrackingTouch(bar: SeekBar) {} })
  renderJob = scope.launch { while (isActive) { if (!seek.isPressed && fast.player.duration > 0) seek.progress = (fast.player.currentPosition * 1000 / fast.player.duration).toInt(); delay(100) } }
  try { local?.open(uri, flags) } catch (_: Exception) { Toast.makeText(activity, "Cannot open this file. Choose a supported video up to 5 GB.", Toast.LENGTH_LONG).show(); showQuick() }
 }
 fun pause() { fast.player.pause() }
 override fun close() { if (released) return; released = true; renderJob?.cancel(); countryJob?.cancel(); vm.close(); scope.cancel(); surface?.let { fast.player.clearVideoSurface(it); it.release() }; surface = null; immersive?.close(); fast.close(); if (dialog.isShowing) dialog.dismiss() }
}
