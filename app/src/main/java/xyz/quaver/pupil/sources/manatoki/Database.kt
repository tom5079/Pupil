/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.sources.manatoki

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.sql.Timestamp

@Entity
data class Favorite(
    @PrimaryKey val itemID: String
)

@Entity
data class Bookmark(
    @PrimaryKey val itemID: String,
    val page: Int
)

@Entity
data class History(
    @PrimaryKey val itemID: String,
    val parent: String,
    val page: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteDao {
    @Query("SELECT EXISTS(SELECT * FROM favorite WHERE itemID = :itemID)")
    fun contains(itemID: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)
    suspend fun insert(itemID: String) = insert(Favorite(itemID))

    @Delete
    suspend fun delete(favorite: Favorite)
    suspend fun delete(itemID: String) = delete(Favorite(itemID))
}

@Dao
interface BookmarkDao {

}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History)
    suspend fun insert(itemID: String, parent: String, page: Int) = insert(History(itemID, parent, page))

    @Query("DELETE FROM history WHERE itemID = :itemID")
    suspend fun delete(itemID: String)

    @Query("SELECT parent FROM (SELECT parent, max(timestamp) as t FROM history GROUP BY parent) ORDER BY t DESC")
    fun getRecentManga(): Flow<List<String>>

    @Query("SELECT itemID FROM history WHERE parent = :parent ORDER BY timestamp DESC")
    suspend fun getAll(parent: String): List<String>
}

@Database(entities = [Favorite::class, Bookmark::class, History::class], version = 1, exportSchema = false)
abstract class ManatokiDatabase: RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
}