package com.khaled.gdgfinder.search

import android.location.Location
import com.khaled.gdgfinder.network.GdgApiService
import com.khaled.gdgfinder.network.GdgChapter
import com.khaled.gdgfinder.network.GdgResponse
import com.khaled.gdgfinder.network.LatLong
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class GdgChapterRepository(gdgApiService: GdgApiService) {

    private val request = gdgApiService.getChapters()

    private var inProgressSort: Deferred<SortedData>? = null

    var isFullyInitialized = false
        private set

    suspend fun getChaptersForFilter(filter: String?): List<GdgChapter> {
        val data = sortedData()
        return when(filter) {
            null -> data.chapters
            else -> data.chaptersByRegion.getOrElse(filter) { emptyList() }
        }
    }

    suspend fun getFilters(): List<String> = sortedData().filters

    private suspend fun sortedData(): SortedData = withContext(Dispatchers.Main) {
        inProgressSort?.await() ?: doSortData()
    }

    private suspend fun doSortData(location: Location? = null): SortedData {
        val result = coroutineScope {
            val deferred = async { SortedData.from(request.await(), location) }
            inProgressSort = deferred
            deferred.await()
        }
        return result
    }

    suspend fun onLocationChanged(location: Location) {
        withContext(Dispatchers.Main) {
            isFullyInitialized = true

            inProgressSort?.cancel()

            doSortData(location)
        }
    }

    private class SortedData private constructor(
        val chapters: List<GdgChapter>,
        val filters: List<String>,
        val chaptersByRegion: Map<String, List<GdgChapter>>
    ) {

        companion object {
            suspend fun from(response: GdgResponse, location: Location?): SortedData {
                return withContext(Dispatchers.Default) {
                    val chapters: List<GdgChapter> = response.chapters.sortByDistanceFrom(location)
                    val filters: List<String> = chapters.map { it.region } .distinctBy { it }
                    val chaptersByRegion: Map<String, List<GdgChapter>> = chapters.groupBy { it.region }
                    SortedData(chapters, filters, chaptersByRegion)
                }

            }

            private fun List<GdgChapter>.sortByDistanceFrom(currentLocation: Location?): List<GdgChapter> {
                currentLocation ?: return this

                return sortedBy { distanceBetween(it.geo, currentLocation) }
            }

            private fun distanceBetween(start: LatLong, currentLocation: Location): Float {
                val results = FloatArray(3)
                Location.distanceBetween(start.lat, start.long, currentLocation.latitude, currentLocation.longitude, results)
                return results[0]
            }
        }
    }
}