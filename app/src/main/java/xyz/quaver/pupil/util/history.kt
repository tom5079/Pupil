package xyz.quaver.pupil.util

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.parseList
import kotlinx.serialization.stringify
import java.io.File


class Histories(private val file: File) : ArrayList<Int>() {

    init {
        if (!file.exists())
            file.parentFile.mkdirs()

        try {
            load()
        } catch (e: Exception) {
            save()
        }
    }

    companion object {
        lateinit var default: Histories

        fun load(file: File) : Histories {
            return Histories(file).load()
        }
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun load() : Histories {
        return apply {
            super.clear()
            addAll(
                Json(JsonConfiguration.Stable).parseList(
                    file.bufferedReader().use { it.readText() }
                )
            )
        }
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun save() {
        file.writeText(Json(JsonConfiguration.Stable).stringify(this))
    }

    override fun add(element: Int): Boolean {
        load()

        if (contains(element))
            super.remove(element)

        super.add(0, element)

        save()

        return true
    }

    override fun remove(element: Int): Boolean {
        load()
        val retval = super.remove(element)
        save()

        return retval
    }

    override fun clear() {
        super.clear()
        save()
    }
}