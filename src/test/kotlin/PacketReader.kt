import CharacterConstants.Companion
import ReaderEvents.BYTE
import ReaderEvents.CTRL
import ReaderEvents.ESC
import ReaderStates.CHKSUM
import ReaderStates.END
import ReaderStates.RCVCHK
import ReaderStates.RCVCHKESC
import ReaderStates.RCVDATA
import ReaderStates.RCVESC
import ReaderStates.RCVPCKT
import ReaderStates.START
import CharacterConstants.*
import io.jumpco.open.kfsm.stateMachine
import java.io.ByteArrayOutputStream

class Block {
    val byteArrayOutputStream = ByteArrayOutputStream(32)
    fun addByte(byte: Int) {
        byteArrayOutputStream.write(byte)
    }
}

interface ProtocolHandler {
    fun sendNACK()
    fun sendACK()
}

interface PacketHandler : ProtocolHandler {
    val checksumValid: Boolean
    fun print()
    fun addField()
    fun endField()
    fun addByte(byte: Int?)
    fun addChecksum(byte: Int?)
    fun checksum()
}

class ProtocolSender : ProtocolHandler {
    override fun sendNACK() {
        println("NACK")
    }

    override fun sendACK() {
        println("ACK")
    }
}

class Packet(private val protocolHandler: ProtocolHandler) : PacketHandler,
    ProtocolHandler by protocolHandler {
    val fields = mutableListOf<ByteArray>()
    private var currentField: Block? = null
    private var _checksumValid: Boolean = false

    override val checksumValid: Boolean
        get() = _checksumValid
    private val checkSum = Block()

    override fun print() {
        println("Checksum:$checksumValid:Fields:${fields.size}")
        fields.forEachIndexed { index, bytes ->
            print("FLD:$index:")
            bytes.forEach { byte ->
                val hex = byte.toString(16).padStart(2, '0')
                print(" $hex")
            }
            println()
        }
        println()
    }

    override fun addField() {
        currentField = Block()
    }

    override fun endField() {
        val field = currentField
        require(field != null) { "expected currentField to have a value" }
        fields.add(field.byteArrayOutputStream.toByteArray())
        currentField = null
    }

    override fun addByte(byte: Int?) {
        require(byte != null) { "argument required" }
        val field = currentField
        require(field != null) { "expected currentField to have a value" }
        field.addByte(byte)
    }

    override fun addChecksum(byte: Int?) {
        require(byte != null) { "argument required" }
        checkSum.addByte(byte)
    }

    override fun checksum() {
        require(checkSum.byteArrayOutputStream.size() > 0)
        val checksumBytes = checkSum.byteArrayOutputStream.toByteArray()
        _checksumValid = if (checksumBytes.size == fields.size) {
            checksumBytes.mapIndexed { index, cs ->
                cs == fields[index][0]
            }.reduce { a, b -> a && b }
        } else {
            false
        }
    }
}

class CharacterConstants {
    companion object {
        const val SOH = 0x01
        const val STX = 0x02
        const val ETX = 0x03
        const val EOT = 0x04
        const val ACK = 0x06
        const val NAK = 0x15
        const val ESC = 0x1b
    }
}

/**
 * CTRL :
 * BYTE everything else
 */
enum class ReaderEvents {
    BYTE, // everything else
    CTRL, // SOH, EOT, STX, ETX, ACK, NAK
    ESC // ESC = 0x1B
}

enum class ReaderStates {
    START,
    RCVPCKT,
    RCVDATA,
    RCVESC,
    RCVCHK,
    RCVCHKESC,
    CHKSUM,
    END
}

class PacketReaderFSM(private val packetHandler: PacketHandler) {
    companion object {
        // tag::definition[]
        val definition = stateMachine(
            ReaderStates.values().toSet(),
            ReaderEvents.values().toSet(),
            PacketHandler::class,
            Int::class
        ) {
            defaultInitialState = START
            default {
                onEvent(BYTE to END) {
                    sendNACK()
                }
                onEvent(CTRL to END) {
                    sendNACK()
                }
                onEvent(ESC to END) {
                    sendNACK()
                }
            }
            whenState(START) {
                onEvent(CTRL to RCVPCKT, guard = { it == CharacterConstants.SOH }) {}
            }
            whenState(RCVPCKT) {
                onEvent(CTRL to RCVDATA, guard = { it == CharacterConstants.STX }) {
                    addField()
                }
                onEvent(BYTE to RCVCHK) {
                    addChecksum(it)
                }
            }
            whenState(RCVDATA) {
                onEvent(BYTE) {
                    addByte(it)
                }
                onEvent(CTRL to RCVPCKT, guard = { it == CharacterConstants.ETX }) {
                    endField()
                }
                onEvent(ESC to RCVESC) {}
            }
            whenState(RCVESC) {
                onEvent(ESC to RCVDATA) {
                    addByte(CharacterConstants.ESC)
                }
                onEvent(CTRL to RCVDATA) {
                    addByte(it)
                }
            }
            whenState(RCVCHK) {
                onEvent(BYTE) {
                    addChecksum(it)
                }
                onEvent(ESC to RCVCHKESC) {}
                onEvent(CTRL to CHKSUM, guard = { it == CharacterConstants.EOT }) {
                    checksum()
                }
            }
            whenState(CHKSUM) {
                automatic(END, guard = { !checksumValid }) {
                    sendNACK()
                }
                automatic(END, guard = { checksumValid }) {
                    sendACK()
                }
            }
            whenState(RCVCHKESC) {
                onEvent(ESC to RCVCHK) {
                    addChecksum(CharacterConstants.ESC)
                }
                onEvent(CTRL to RCVCHK) { byte ->
                    require(byte != null)
                    addChecksum(byte)
                }
            }
        }.build()
        // end::definition[]
    }

    private val fsm = definition.create(packetHandler)
    fun receiveByte(byte: Int) {
        when (byte) {
            CharacterConstants.ESC -> fsm.sendEvent(ReaderEvents.ESC, CharacterConstants.ESC)
            CharacterConstants.SOH,
            CharacterConstants.EOT,
            CharacterConstants.ETX,
            CharacterConstants.STX,
            CharacterConstants.ACK,
            CharacterConstants.NAK -> fsm.sendEvent(CTRL, byte)
            else                   -> fsm.sendEvent(BYTE, byte)
        }
    }
}
