package xyz.quaver.pupil.db

import androidx.lifecycle.LiveData
import androidx.room.*
import xyz.quaver.pupil.sources.ItemInfo

@Entity(primaryKeys = ["source", "itemID"])
data class Bookmark(
    val source: String,
    val itemID: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark")
    fun getAll(): LiveData<List<Bookmark>>

    @Query("SELECT itemID FROM bookmark WHERE source = :source")
    fun getAll(source: String): LiveData<List<String>>

    @Query("SELECT EXISTS(SELECT * FROM bookmark WHERE source = :source AND itemID = :itemID)")
    fun contains(source: String, itemID: String): LiveData<Boolean>

    fun contains(bookmark: Bookmark) = contains(bookmark.source, bookmark.itemID)
    fun contains(itemInfo: ItemInfo) = contains(itemInfo.source, itemInfo.itemID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    suspend fun insert(source: String, itemID: String) = insert(Bookmark(source, itemID))
    suspend fun insert(itemInfo: ItemInfo) = insert(Bookmark(itemInfo.source, itemInfo.itemID))

    @Delete
    suspend fun delete(bookmark: Bookmark)

    suspend fun delete(source: String, itemID: String) = delete(Bookmark(source, itemID))
    suspend fun delete(itemInfo: ItemInfo) = delete(Bookmark(itemInfo.source, itemInfo.itemID))
}