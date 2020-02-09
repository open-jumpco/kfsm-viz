/*
 * Copyright (c) 2019. Open JumpCO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.jumpco.open.kfsm

/**
 * @suppress
 */
class PayingTurnstile(
    val requiredCoins: Int,
    locked: Boolean = true,
    coins: Int = 0
) {
    var coins: Int = coins
        private set
    var locked: Boolean = locked
        private set

    fun unlock() {
        require(locked) { "Cannot unlock when not locked" }
        require(coins >= requiredCoins) { "Not enough coins. ${requiredCoins - coins} required" }
        println("Unlock")
        locked = false
    }

    fun lock() {
        require(!locked) { "Cannot lock when locked" }
        require(coins == 0) { "Coins $coins must be returned" }
        println("Lock")
        locked = true
    }

    fun alarm() {
        println("Alarm")
    }

    fun coin(value: Int?): Int {
        require(value != null) { "argument required" }
        coins += value
        println("Coin received=$value, Total=$coins")
        return coins
    }

    fun returnCoin(returnCoins: Int?) {
        require(returnCoins != null) { "argument required" }
        println("Return Coin:$returnCoins")
        coins -= returnCoins
    }

    fun reset() {
        coins = 0
        println("Reset coins=$coins")
    }

    override fun toString(): String {
        return "Turnstile(locked=$locked,coins=$coins)"
    }
}

/**
 * @suppress
 */
enum class PayingTurnstileStates {
    LOCKED,
    COINS,
    UNLOCKED
}

/**
 * @suppress
 */
enum class PayingTurnstileEvents {
    COIN,
    PASS
}

/**
 * @suppress
 */
class PayingTurnstileFSM(
    turnstile: PayingTurnstile,
    initialState: ExternalState<PayingTurnstileStates>? = null
) {
    val fsm = if (initialState != null) {
        definition.create(turnstile, initialState)
    } else {
        definition.create(
            turnstile,
            PayingTurnstileStates.LOCKED
        )
    }

    fun coin(value: Int) {
        println("sendEvent:COIN:$value")
        fsm.sendEvent(PayingTurnstileEvents.COIN, value)
    }

    fun pass() {
        println("sendEvent:PASS")
        fsm.sendEvent(PayingTurnstileEvents.PASS)
    }

    fun allowedEvents() = fsm.allowed().map { it.name.toLowerCase() }.toSet()
    fun externalState() = fsm.externalState()

    companion object {
        // tag::definition[]
        val definition = stateMachine(
            setOf(PayingTurnstileStates.LOCKED, PayingTurnstileStates.UNLOCKED),
            PayingTurnstileEvents.values().toSet(),
            PayingTurnstile::class,
            Int::class
        ) {
            defaultInitialState = PayingTurnstileStates.LOCKED
            default {
                onEntry { _, targetState, arg ->
                    if (arg != null) {
                        println("entering:$targetState ($arg) for $this")
                    } else {
                        println("entering:$targetState for $this")
                    }
                }
                action { state, event, arg ->
                    if (arg != null) {
                        println("Default action for state($state) -> on($event, $arg) for $this")
                    } else {
                        println("Default action for state($state) -> on($event) for $this")
                    }
                    alarm()
                }
                onExit { startState, _, arg ->
                    if (arg != null) {
                        println("exiting:$startState ($arg) for $this")
                    } else {
                        println("exiting:$startState for $this")
                    }
                }
            }
            stateMap("coins", setOf(PayingTurnstileStates.COINS)) {
                whenState(PayingTurnstileStates.COINS) {
                    automaticPop(PayingTurnstileStates.UNLOCKED, guard = { coins > requiredCoins }) {
                        returnCoin(coins - requiredCoins)
                        unlock()
                        reset()
                    }
                    automaticPop(PayingTurnstileStates.UNLOCKED, guard = { coins == requiredCoins }) {
                        unlock()
                        reset()
                    }
                    onEvent(PayingTurnstileEvents.COIN) {
                        coin(it)
                        if (coins < requiredCoins) {
                            println("Please add ${requiredCoins - coins}")
                        }
                    }
                }
            }
            whenState(PayingTurnstileStates.LOCKED) {
                // The coin brings amount to exact amount
                onEventPush(PayingTurnstileEvents.COIN, "coins", PayingTurnstileStates.COINS) {
                    coin(it)
                    unlock()
                    reset()
                }
                // The coins add up to more than required
                onEventPush(PayingTurnstileEvents.COIN, "coins", PayingTurnstileStates.COINS,
                    guard = { requiredCoins >= coins + (it ?: error("argument required")) }) {
                    coin(it)
                    println("Coins=$coins, Please add ${requiredCoins - coins}")
                }
            }
            whenState(PayingTurnstileStates.UNLOCKED) {
                onEvent(PayingTurnstileEvents.COIN) {
                    returnCoin(coin(it))
                }
                onEvent(PayingTurnstileEvents.PASS to PayingTurnstileStates.LOCKED) {
                    lock()
                }
            }
        }.build()
        // end::definition[]
    }
}
