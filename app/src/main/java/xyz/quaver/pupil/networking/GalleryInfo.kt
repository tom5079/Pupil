package xyz.quaver.pupil.networking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TagLike {
    fun toTag(): SearchQuery.Tag
}

@Serializable
data class Artist(val artist: String): TagLike {
    override fun toTag() = SearchQuery.Tag("artist", artist)
}

@Serializable
data class Group(val group: String): TagLike {
    override fun toTag() = SearchQuery.Tag("group", group)
}

@Serializable
data class Series(val series: String): TagLike {
    override fun toTag() = SearchQuery.Tag("series", series)
}

@Serializable
data class Character(val character: String): TagLike {
    override fun toTag() = SearchQuery.Tag("character", character)
}

@Serializable
data class GalleryTag(
    val tag: String,
    val female: String? = null,
    val male: String? = null
): TagLike {
    override fun toTag() = SearchQuery.Tag(
        if (female.isNullOrEmpty() && male.isNullOrEmpty()) {
            "tag"
        } else if (male.isNullOrEmpty()) {
            "female"
        } else {
            "male"
        },
        tag
    )
}

@Serializable
data class Language(
    @SerialName("galleryid") val galleryID: String,
    val url: String,
    @SerialName("language_localname") val localLanguageName: String,
    val name: String
)

@Serializable
data class GalleryFiles(
    @SerialName("haswebp") val hasWebP: Int = 0,
    @SerialName("hasavif") val hasAVIF: Int = 0,
    @SerialName("hasjxl") val hasJXL: Int = 0,
    val height: Int,
    val width: Int,
    val hash: String,
    val name: String,
)

@Serializable
data class GalleryInfo(
    val id: String,
    val title: String,
    @SerialName("japanese_title") val japaneseTitle: String? = null,
    val language: String? = null,
    val type: String,
    val date: String,
    val artists: List<Artist>? = null,
    val groups: List<Group>? = null,
    @SerialName("parodys") val series: List<Series>? = null,
    val tags: List<GalleryTag>? = null,
    val related: List<Int> = emptyList(),
    val languages: List<Language> = emptyList(),
    val characters: List<Character>? = null,
    @SerialName("scene_indexes") val sceneIndices: List<Int>? = emptyList(),
    val files: List<GalleryFiles> = emptyList()
)
