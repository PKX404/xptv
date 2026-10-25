package com.pkx404.xparty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

data class QuickVideo(val url: String, val title: String, val thumbnail: String?, val durationSeconds: Long, val live: Boolean)
data class QuickSearchState(val query: String = "", val loading: Boolean = false, val videos: List<QuickVideo> = emptyList(), val error: String? = null)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class QuickSearchVM : ViewModel() {
 private val query = MutableStateFlow("")
 private val mutable = MutableStateFlow(QuickSearchState())
 val state: StateFlow<QuickSearchState> = mutable.asStateFlow()
 init { viewModelScope.launch {
  query.debounce(300).distinctUntilChanged().collectLatest { value ->
   if (value.isBlank()) { mutable.value = QuickSearchState(); return@collectLatest }
   mutable.value = QuickSearchState(value, loading = true)
   try {
    val videos = withContext(Dispatchers.IO) {
     NewpipeDownloader.initialize(); val extractor = ServiceList.YouTube.getSearchExtractor(value); extractor.fetchPage()
     extractor.initialPage.items.filterIsInstance<StreamInfoItem>().take(30).map { QuickVideo(it.url, it.name, it.thumbnails.firstOrNull()?.url, it.duration, it.streamType == StreamType.LIVE_STREAM) }
    }
    ensureActive(); mutable.value = QuickSearchState(value, videos = videos)
   } catch (error: CancellationException) { throw error }
   catch (_: Exception) { mutable.value = QuickSearchState(value, error = "Search is unavailable. Try another keyword or retry.") }
  }
 } }
 fun search(value: String) { query.value = value.trim().take(120) }
 fun close() { viewModelScope.cancel() }
}
