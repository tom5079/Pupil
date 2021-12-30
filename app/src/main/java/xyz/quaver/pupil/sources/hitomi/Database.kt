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

package xyz.quaver.pupil.sources.hitomi

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Favorite(
    @PrimaryKey val item: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite")
    fun getAll(): Flow<List<Favorite>>

    @Query("SELECT EXISTS(SELECT * FROM favorite WHERE item = :item)")
    fun contains(item: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)
    suspend fun insert(item: String) = insert(Favorite(item))

    @Delete
    suspend fun delete(favorite: Favorite)
    suspend fun delete(item: String) = delete(Favorite(item))
}

@Database(entities = [Favorite::class], version = 1, exportSchema = false)
abstract class HitomiDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
}