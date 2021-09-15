package xyz.quaver.pupil.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Entity(primaryKeys = ["source", "itemID"])
data class History(
    val source: String,
    val itemID: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history")
    fun getAll(): LiveData<List<History>>

    @Query("SELECT itemID FROM history WHERE source = :source")
    fun getAll(source: String): LiveData<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History)

    @Delete
    suspend fun delete(history: History)
}