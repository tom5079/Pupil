package xyz.quaver.pupil.networking

sealed interface SearchQuery {
    data class Tag(
        val namespace: String?,
        val tag: String
    ): SearchQuery, TagLike {
        companion object {
            fun parseTag(tag: String): Tag {
                val splitTag = tag.split(':', limit =  1)

                return if (splitTag.size == 1) {
                    Tag(null, tag)
                } else {
                    Tag(splitTag[0], splitTag[1])
                }
            }
        }

        override fun toString() = if (namespace == null) tag else "$namespace:$tag"

        override fun toTag() = this
    }


    data class And(
        val queries: List<SearchQuery>
    ): SearchQuery {
        init {
            if (queries.isEmpty()) {
                error("queries cannot be empty")
            }
        }
    }

    data class Or(
        val queries: List<SearchQuery>
    ): SearchQuery {
        init {
            if (queries.isEmpty()) {
                error("queries cannot be empty")
            }
        }
    }

    data class Not(
        val query: SearchQuery
    ): SearchQuery

}
