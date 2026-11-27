package com.moonreader.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.HistoryEntry
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackStatus
import com.moonreader.app.domain.model.UpdateEntry
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mangas")
data class MangaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String,
    val artist: String,
    val description: String,
    val genresCsv: String,
    val status: String,
    val coverUrl: String,
    val inLibrary: Boolean,
    val categoryId: Long,
    val lastUpdatedAt: Long,
    val addedAt: Long
) {
    fun toDomain(unreadCount: Int = 0, totalChapters: Int = 0): Manga = Manga(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        author = author,
        artist = artist,
        description = description,
        genres = genresCsv.split("|").filter { it.isNotBlank() },
        status = runCatching { MangaStatus.valueOf(status) }.getOrDefault(MangaStatus.UNKNOWN),
        coverUrl = coverUrl,
        inLibrary = inLibrary,
        categoryId = categoryId,
        unreadCount = unreadCount,
        totalChapters = totalChapters,
        lastUpdatedAt = lastUpdatedAt,
        addedAt = addedAt
    )

    companion object {
        fun fromDomain(m: Manga): MangaEntity = MangaEntity(
            id = m.id,
            sourceId = m.sourceId,
            url = m.url,
            title = m.title,
            author = m.author,
            artist = m.artist,
            description = m.description,
            genresCsv = m.genres.joinToString("|"),
            status = m.status.name,
            coverUrl = m.coverUrl,
            inLibrary = m.inLibrary,
            categoryId = m.categoryId,
            lastUpdatedAt = m.lastUpdatedAt,
            addedAt = m.addedAt
        )
    }
}

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val url: String,
    val name: String,
    val chapterNumber: Float,
    val scanlator: String,
    val uploadedAt: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Int,
    val totalPages: Int,
    val downloaded: Boolean
) {
    fun toDomain(): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        uploadedAt = uploadedAt,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        totalPages = totalPages,
        downloaded = downloaded
    )

    companion object {
        fun fromDomain(c: Chapter): ChapterEntity = ChapterEntity(
            id = c.id,
            mangaId = c.mangaId,
            url = c.url,
            name = c.name,
            chapterNumber = c.chapterNumber,
            scanlator = c.scanlator,
            uploadedAt = c.uploadedAt,
            read = c.read,
            bookmark = c.bookmark,
            lastPageRead = c.lastPageRead,
            totalPages = c.totalPages,
            downloaded = c.downloaded
        )
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sortOrder: Int
) {
    fun toDomain(): Category = Category(id = id, name = name, sortOrder = sortOrder)
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val mangaId: String,
    val chapterId: String,
    val lastPageRead: Int,
    val readAt: Long
)

@Entity(tableName = "track_records")
data class TrackRecordEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val trackerId: String,
    val remoteId: Long,
    val libraryEntryId: String,
    val title: String,
    val coverUrl: String,
    val status: String,
    val lastChapterRead: Float,
    val totalChapters: Int,
    val score: Float,
    val remoteUrl: String,
    val updatedAt: Long
) {
    fun toDomain(): TrackRecord = TrackRecord(
        id = id,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryEntryId = libraryEntryId,
        title = title,
        coverUrl = coverUrl,
        status = runCatching { TrackStatus.valueOf(status) }.getOrDefault(TrackStatus.READING),
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        score = score,
        remoteUrl = remoteUrl,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(t: TrackRecord): TrackRecordEntity = TrackRecordEntity(
            id = t.id,
            mangaId = t.mangaId,
            trackerId = t.trackerId,
            remoteId = t.remoteId,
            libraryEntryId = t.libraryEntryId,
            title = t.title,
            coverUrl = t.coverUrl,
            status = t.status.name,
            lastChapterRead = t.lastChapterRead,
            totalChapters = t.totalChapters,
            score = t.score,
            remoteUrl = t.remoteUrl,
            updatedAt = t.updatedAt
        )
    }
}

data class MangaWithStatsRow(
    val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String,
    val artist: String,
    val description: String,
    val genresCsv: String,
    val status: String,
    val coverUrl: String,
    val inLibrary: Boolean,
    val categoryId: Long,
    val lastUpdatedAt: Long,
    val addedAt: Long,
    val unreadCount: Int,
    val totalChapters: Int
) {
    fun toDomain(): Manga = Manga(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        author = author,
        artist = artist,
        description = description,
        genres = genresCsv.split("|").filter { it.isNotBlank() },
        status = runCatching { MangaStatus.valueOf(status) }.getOrDefault(MangaStatus.UNKNOWN),
        coverUrl = coverUrl,
        inLibrary = inLibrary,
        categoryId = categoryId,
        unreadCount = unreadCount,
        totalChapters = totalChapters,
        lastUpdatedAt = lastUpdatedAt,
        addedAt = addedAt
    )
}

data class HistoryJoinRow(
    val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val sourceId: String,
    val chapterName: String,
    val chapterNumber: Float,
    val lastPageRead: Int,
    val readAt: Long
) {
    fun toDomain(): HistoryEntry = HistoryEntry(
        chapterId = chapterId,
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        mangaCoverUrl = mangaCoverUrl,
        sourceId = sourceId,
        chapterName = chapterName,
        chapterNumber = chapterNumber,
        lastPageRead = lastPageRead,
        readAt = readAt
    )
}

data class UpdateJoinRow(
    val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val chapterName: String,
    val scanlator: String,
    val uploadedAt: Long,
    val read: Boolean,
    val downloaded: Boolean
) {
    fun toDomain(): UpdateEntry = UpdateEntry(
        chapterId = chapterId,
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        mangaCoverUrl = mangaCoverUrl,
        chapterName = chapterName,
        scanlator = scanlator,
        uploadedAt = uploadedAt,
        read = read,
        downloaded = downloaded
    )
}

@Dao
interface MangaDao {
    @Query(
        """
        SELECT m.*,
               (SELECT COUNT(*) FROM chapters c WHERE c.mangaId = m.id AND c.read = 0) AS unreadCount,
               (SELECT COUNT(*) FROM chapters c WHERE c.mangaId = m.id) AS totalChapters
        FROM mangas m
        WHERE m.inLibrary = 1
        ORDER BY m.addedAt DESC, m.title ASC
        """
    )
    fun observeLibrary(): Flow<List<MangaWithStatsRow>>

    @Query("SELECT * FROM mangas WHERE inLibrary = 1")
    suspend fun getLibraryMangas(): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE id = :id LIMIT 1")
    fun observeMangaById(id: String): Flow<MangaEntity?>

    @Query("SELECT * FROM mangas WHERE id = :id LIMIT 1")
    suspend fun getMangaById(id: String): MangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManga(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(manga: MangaEntity)

    @Query("UPDATE mangas SET inLibrary = :inLibrary, categoryId = :categoryId, addedAt = :addedAt WHERE id = :id")
    suspend fun updateLibraryStatus(id: String, inLibrary: Boolean, categoryId: Long, addedAt: Long)

    @Query("UPDATE mangas SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategory(oldCategoryId: Long, newCategoryId: Long)

    @Query("DELETE FROM mangas WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM mangas WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber DESC, uploadedAt DESC")
    fun observeChapters(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber DESC, uploadedAt DESC")
    suspend fun getChapters(mangaId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET read = :read, lastPageRead = :lastPageRead WHERE id = :chapterId")
    suspend fun updateProgress(chapterId: String, read: Boolean, lastPageRead: Int)

    @Query("UPDATE chapters SET totalPages = :totalPages WHERE id = :chapterId")
    suspend fun updateTotalPages(chapterId: String, totalPages: Int)

    @Query("UPDATE chapters SET bookmark = :bookmark WHERE id = :chapterId")
    suspend fun updateBookmark(chapterId: String, bookmark: Boolean)

    @Query("UPDATE chapters SET downloaded = :downloaded WHERE id = :chapterId")
    suspend fun updateDownloaded(chapterId: String, downloaded: Boolean)

    @Query("UPDATE chapters SET read = :read WHERE id IN (:chapterIds)")
    suspend fun markAllRead(chapterIds: List<String>, read: Boolean)

    @Query("DELETE FROM chapters WHERE mangaId LIKE :prefix")
    suspend fun deleteByMangaPrefix(prefix: String)

    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: String)

    @Query(
        """
        SELECT c.id AS chapterId, m.id AS mangaId, m.title AS mangaTitle, m.coverUrl AS mangaCoverUrl,
               c.name AS chapterName, c.scanlator AS scanlator, c.uploadedAt AS uploadedAt,
               c.read AS read, c.downloaded AS downloaded
        FROM chapters c
        INNER JOIN mangas m ON c.mangaId = m.id
        WHERE m.inLibrary = 1
        ORDER BY c.uploadedAt DESC, c.chapterNumber DESC
        LIMIT 100
        """
    )
    fun observeRecentUpdates(): Flow<List<UpdateJoinRow>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id AND id != 1")
    suspend fun deleteCategory(id: Long)
}

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT h.chapterId, h.mangaId, m.title AS mangaTitle, m.coverUrl AS mangaCoverUrl,
               m.sourceId AS sourceId, c.name AS chapterName, c.chapterNumber AS chapterNumber,
               h.lastPageRead, h.readAt
        FROM history h
        INNER JOIN mangas m ON h.mangaId = m.id
        INNER JOIN chapters c ON h.chapterId = c.id
        ORDER BY h.readAt DESC
        """
    )
    fun observeHistory(): Flow<List<HistoryJoinRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE mangaId = :mangaId")
    suspend fun deleteByManga(mangaId: String)

    @Query("DELETE FROM history WHERE mangaId LIKE :prefix")
    suspend fun deleteByMangaPrefix(prefix: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM track_records WHERE mangaId = :mangaId ORDER BY trackerId ASC")
    fun observeTracksForManga(mangaId: String): Flow<List<TrackRecordEntity>>

    @Query("SELECT * FROM track_records WHERE mangaId = :mangaId ORDER BY trackerId ASC")
    suspend fun getTracksForManga(mangaId: String): List<TrackRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackRecordEntity)

    @Query("DELETE FROM track_records WHERE id = :id")
    suspend fun deleteTrackById(id: String)

    @Query("DELETE FROM track_records WHERE mangaId = :mangaId")
    suspend fun deleteTracksForManga(mangaId: String)
}

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        HistoryEntity::class,
        TrackRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MoonDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun trackDao(): TrackDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_records` (
                        `id` TEXT NOT NULL,
                        `mangaId` TEXT NOT NULL,
                        `trackerId` TEXT NOT NULL,
                        `remoteId` INTEGER NOT NULL,
                        `libraryEntryId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `coverUrl` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastChapterRead` REAL NOT NULL,
                        `totalChapters` INTEGER NOT NULL,
                        `score` REAL NOT NULL,
                        `remoteUrl` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): MoonDatabase =
            Room.databaseBuilder(context.applicationContext, MoonDatabase::class.java, "moonreader.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
