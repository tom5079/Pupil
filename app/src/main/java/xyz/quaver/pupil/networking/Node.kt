@file:OptIn(ExperimentalUnsignedTypes::class)

package xyz.quaver.pupil.networking

import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min

private fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

private fun hashTerm(term: String): UByteArray =
    sha256(term.toByteArray()).sliceArray(0..<4).toUByteArray()

data class Node(
    val keys: List<Key>,
    val datas: List<Data>,
    val subNodeAddresses: List<Long>
) {
    data class Key(
        private val key: UByteArray
    ): Comparable<Key> {

        constructor(term: String): this(hashTerm(term))

        override fun compareTo(other: Key): Int {
            val minSize = min(this.key.size, other.key.size)

            for (i in 0..<minSize) {
                if (this.key[i] < other.key[i]) {
                    return -1
                } else if(this.key[i] > other.key[i]) {
                    return 1
                }
            }

            return 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Key

            return key.contentEquals(other.key)
        }

        override fun hashCode(): Int {
            return key.contentHashCode()
        }
    }

    data class Data(
        val offset: Long,
        val length: Int
    )

    companion object {
        fun decodeNode(buffer: ByteBuffer): Node {
            val numberOfKeys = buffer.int
            val keys = mutableListOf<Node.Key>()

            for (i in 0..<numberOfKeys) {
                val keySize = buffer.int

                val key = ByteArray(keySize)
                buffer.get(key)

                keys.add(Node.Key(key.toUByteArray()))
            }

            val numberOfDatas = buffer.int
            val datas = mutableListOf<Data>()

            for (i in 0..<numberOfDatas) {
                val offset = buffer.long
                val length = buffer.int

                datas.add(Data(offset, length))
            }

            val numberOfSubNodeAddresses = B+1
            val subNodeAddresses = mutableListOf<Long>()

            for (i in 0..<numberOfSubNodeAddresses) {
                val subNodeAddress = buffer.long
                subNodeAddresses.add(subNodeAddress)
            }

            return Node(keys, datas, subNodeAddresses)
        }
    }

    val isLeaf: Boolean = subNodeAddresses.all { it == 0L }

    fun locateKey(target: Key): Pair<Boolean, Int> {
        val index = keys.indexOfFirst { key -> key <= target }

        if (index == -1) {
            return Pair(false, keys.size)
        }

        return Pair(keys[index] == target, index)
    }
}

