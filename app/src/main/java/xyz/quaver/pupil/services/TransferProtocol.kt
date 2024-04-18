package xyz.quaver.pupil.services

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

const val TRANSFER_PROTOCOL_VERSION: UByte = 1u

enum class TransferType(val value: UByte) {
    INVALID(255u),
    HELLO(0u),
    PING(1u),
    PONG(2u),
    LIST_REQUEST(3u),
    LIST_RESPONSE(4u),
}

sealed interface TransferPacket {
    val type: TransferType

    suspend fun writeToChannel(channel: ByteWriteChannel)

    data class Hello(val version: UByte = TRANSFER_PROTOCOL_VERSION): TransferPacket {
        override val type = TransferType.HELLO

        override suspend fun writeToChannel(channel: ByteWriteChannel) {
            channel.writeByte(type.value.toByte())
            channel.writeByte(version.toByte())
        }
    }

    data object Ping: TransferPacket {
        override val type = TransferType.PING
        override suspend fun writeToChannel(channel: ByteWriteChannel) {
            channel.writeByte(type.value.toByte())
        }
    }

    data object Pong: TransferPacket {
        override val type = TransferType.PONG
        override suspend fun writeToChannel(channel: ByteWriteChannel) {
            channel.writeByte(type.value.toByte())
        }
    }

    data object ListRequest: TransferPacket {
        override val type = TransferType.LIST_REQUEST
        override suspend fun writeToChannel(channel: ByteWriteChannel) {
            channel.writeByte(type.value.toByte())
        }
    }

    data object Invalid: TransferPacket {
        override val type = TransferType.INVALID
        override suspend fun writeToChannel(channel: ByteWriteChannel) {
            channel.writeByte(type.value.toByte())
        }
    }

    data class ListResponse(
        val favoritesCount: Int,
        val historyCount: Int,
        val downloadsCount: Int,
    ): TransferPacket {
        override val type = TransferType.LIST_RESPONSE

        override suspend fun writeToChannel(channel: ByteWriteChannel) {
            channel.writeByte(type.value.toByte())
            channel.writeInt(favoritesCount)
            channel.writeInt(historyCount)
            channel.writeInt(downloadsCount)
        }
    }

    companion object {
        suspend fun readFromChannel(channel: ByteReadChannel): TransferPacket {
            return when(val type = channel.readByte().toUByte()) {
                TransferType.HELLO.value -> {
                    val version = channel.readByte().toUByte()
                    Hello(version)
                }
                TransferType.PING.value -> Ping
                TransferType.PONG.value -> Pong
                TransferType.LIST_REQUEST.value -> ListRequest
                TransferType.LIST_RESPONSE.value -> {
                    val favoritesCount = channel.readInt()
                    val historyCount = channel.readInt()
                    val downloadsCount = channel.readInt()
                    ListResponse(favoritesCount, historyCount, downloadsCount)
                }
                else -> throw IllegalArgumentException("Unknown packet type: $type")
            }
        }
    }
}