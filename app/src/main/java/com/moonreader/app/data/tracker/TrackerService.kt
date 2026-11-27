package com.moonreader.app.data.tracker

import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackSearchResult
import com.moonreader.app.domain.model.TrackerAccountInfo

interface TrackerService {
    val id: String
    val name: String
    val requiresClientIdForLogin: Boolean
    val supportsPasswordLogin: Boolean

    fun getAccountInfo(): TrackerAccountInfo

    suspend fun search(query: String): Result<List<TrackSearchResult>>

    suspend fun bind(mangaId: String, candidate: TrackSearchResult): Result<TrackRecord>

    suspend fun update(track: TrackRecord): Result<TrackRecord>

    fun logout()
}
