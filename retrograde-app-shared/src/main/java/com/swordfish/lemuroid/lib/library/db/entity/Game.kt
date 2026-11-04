/*
 * Game.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library.db.entity

import androidx.recyclerview.widget.DiffUtil
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.swordfish.lemuroid.lib.library.GameSystem
import java.io.Serializable
import java.net.URLEncoder

@Entity(
    tableName = "games",
    indices = [
        Index("id", unique = true),
        Index("fileUri", unique = true),
        Index("title"),
        Index("systemId"),
        Index("lastIndexedAt"),
        Index("lastPlayedAt"),
        Index("isFavorite"),
    ],
)
data class Game(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileName: String,
    val fileUri: String,
    val title: String,
    val systemId: String,
    val developer: String?,
    val coverFrontUrl: String?,
    val lastIndexedAt: Long,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false,
    val customName: String? = null,
    val customCoverUri: String? = null,
    @ColumnInfo(defaultValue = "0")
    val playCount: Int = 0,
) : Serializable {

    fun displayName(): String = customName ?: title
    /**
     * A custom image wins. Otherwise a renamed game looks for its cover under the new name, and
     * keeps the cover it originally had as a fallback.
     */
    fun displayCoverUrl(): String? {
        customCoverUri?.let { return it }

        val renamed = customName?.trim()?.takeIf { it.isNotEmpty() } ?: return coverFrontUrl
        val systemFolder =
            runCatching { GameSystem.findById(systemId).libretroFullName }.getOrNull()
                ?: return coverFrontUrl

        val safeName = renamed.replace(UNSAFE_THUMBNAIL_CHARACTERS, "_")
        val renamedUrl = "http://thumbnails.libretro.com/$systemFolder/Named_Boxarts/$safeName.png"
        return coverFrontUrl
            ?.let { "$renamedUrl?fallback=${URLEncoder.encode(it, "UTF-8")}" }
            ?: renamedUrl
    }

    companion object {
        private val UNSAFE_THUMBNAIL_CHARACTERS = Regex("[&*/:`<>?\\\\|#%]")

        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<Game>() {
                override fun areItemsTheSame(
                    oldItem: Game,
                    newItem: Game,
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: Game,
                    newItem: Game,
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
