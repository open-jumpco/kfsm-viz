/*
 * Copyright (c) 2020-2021. Open JumpCO
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */


import io.jumpco.open.example.kfsm.TurnstileEvent.COIN
import io.jumpco.open.example.kfsm.TurnstileEvent.PASS
import io.jumpco.open.example.kfsm.TurnstileState.LOCKED
import io.jumpco.open.example.kfsm.TurnstileState.UNLOCKED
import io.jumpco.open.kfsm.stateMachine


enum class SimpleTurnstileEvent {
    COIN,
    PASS
}

enum class SimpleTurnstileState {
    LOCKED,
    UNLOCKED
}

class SimpleTurnstileContext(locked: Boolean = true) {
    var locked: Boolean = locked
        private set

    fun unlock() {
        require(locked) { "Cannot unlock when not locked" }
        println("Unlock")
        locked = false
    }

    fun lock() {
        require(!locked) { "Cannot lock when locked" }
        println("Lock")
        locked = true
    }

    fun alarm() {
        println("Alarm")
    }

    fun returnCoin() {
        println("Return Coin")
    }

    override fun toString(): String {
        return "Turnstile(locked=$locked)"
    }
}

class SimpleTurnstileFSM(context: SimpleTurnstileContext) {
    private val fsm = definition.create(context)

    fun coin() = fsm.sendEvent(SimpleTurnstileEvent.COIN)
    fun pass() = fsm.sendEvent(SimpleTurnstileEvent.PASS)

    companion object {
        // tag::definition[]
        val definition = stateMachine(
            SimpleTurnstileState.values().toSet(),
            SimpleTurnstileEvent.values().toSet(),
            SimpleTurnstileContext::class
        ) {
            initialState { if (locked) SimpleTurnstileState.LOCKED else SimpleTurnstileState.UNLOCKED }
            default {
                action { _, _, _ ->
                    error("Alarm")
                }
            }
            whenState(SimpleTurnstileState.LOCKED) {
                onEvent(SimpleTurnstileEvent.COIN to SimpleTurnstileState.UNLOCKED) {
                    unlock()
                }
            }
            whenState(SimpleTurnstileState.UNLOCKED) {
                onEvent(SimpleTurnstileEvent.PASS to SimpleTurnstileState.LOCKED) {
                    lock()
                }
                onEvent(SimpleTurnstileEvent.COIN) {
                    returnCoin()
                }
            }
        }.build()
        // end::definition[]
    }
}
