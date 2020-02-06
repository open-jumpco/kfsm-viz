package io.jumpco.open.example.kfsm

import io.jumpco.open.example.kfsm.TurnstileEvent.COIN
import io.jumpco.open.example.kfsm.TurnstileEvent.PASS
import io.jumpco.open.example.kfsm.TurnstileState.LOCKED
import io.jumpco.open.example.kfsm.TurnstileState.UNLOCKED
import io.jumpco.open.kfsm.stateMachine

data class TurnstileInfo(
    val id: Int,
    val locked: Boolean = true,
    val message: String = ""
)

enum class TurnstileEvent {
    COIN,
    PASS
}

enum class TurnstileState {
    LOCKED,
    UNLOCKED
}

class TurnstileAlarmException(message: String) : Exception(message) {
}

class TurnstileFSM(turnstile: TurnstileInfo) {
    private val fsm = definition.create(turnstile)

    fun coin(info: TurnstileInfo) = fsm.sendEvent(COIN, info)
    fun pass(info: TurnstileInfo) = fsm.sendEvent(PASS, info)
    fun event(event: String, info: TurnstileInfo) = fsm.sendEvent(TurnstileEvent.valueOf(event.toUpperCase()), info)
    fun allowed(event: TurnstileEvent) = fsm.allowed().contains(event)

    companion object {
        // tag::definition[]
        val definition = stateMachine(
            TurnstileState.values().toSet(),
            TurnstileEvent.values().toSet(),
            TurnstileInfo::class,
            TurnstileInfo::class,
            TurnstileInfo::class
        ) {
            defaultInitialState = LOCKED
            initialState { if (locked) LOCKED else UNLOCKED }
            default {
                action { _, _, _ ->
                    throw TurnstileAlarmException("Alarm")
                }
            }
            whenState(LOCKED) {
                onEvent(COIN to UNLOCKED) { info ->
                    require(info != null) { "Info required" }
                    info.copy(locked = false, message = "")
                }
            }
            whenState(UNLOCKED) {
                onEvent(PASS to LOCKED) { info ->
                    require(info != null) { "Info required" }
                    info.copy(locked = true, message = "")
                }
                onEvent(COIN) { info ->
                    require(info != null) { "Info required" }
                    info.copy(message = "Return Coin")
                }
            }
        }.build()
        // end::definition[]
    }
}
