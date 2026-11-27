package com.moonreader.app.data.tracker

import com.moonreader.app.domain.model.TrackerAccountInfo

class TrackerManager(
    val aniList: AniListTracker,
    val myAnimeList: MyAnimeListTracker,
    val kitsu: KitsuTracker
) {
    private val trackers: Map<String, TrackerService> = linkedMapOf(
        AniListTracker.TRACKER_ID to aniList,
        MyAnimeListTracker.TRACKER_ID to myAnimeList,
        KitsuTracker.TRACKER_ID to kitsu
    )

    fun getTracker(id: String): TrackerService? = trackers[id]

    fun getAllTrackers(): List<TrackerService> = trackers.values.toList()

    fun getAccountInfos(): List<TrackerAccountInfo> = trackers.values.map { it.getAccountInfo() }
}
