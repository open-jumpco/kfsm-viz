package io.jumpco.open.kfsm

import io.jumpco.open.kfsm.TransitionType.DEFAULT
import io.jumpco.open.kfsm.TransitionType.NORMAL
import java.io.PrintWriter
import java.io.StringWriter

data class TransitionView(
    val sourceMap: String? = null,
    val start: String?,
    val event: String?,
    val target: String? = null,
    val targetMap: String? = null,
    val action: String? = null,
    val automatic: Boolean = false,
    val type: TransitionType = NORMAL,
    val guard: String? = null
)

fun <S, E, C, A, R> visualize(definition: StateMachineDefinition<S, E, C, A, R>): Iterable<TransitionView> {
    val output = mutableListOf<TransitionView>()
    if (definition.defaultInitialState != null) {
        output.add(TransitionView("default", "<<start>>", null, definition.defaultInitialState.toString()))
    }
    val stateMap = definition.defaultStateMap
    makeStateMap("default", stateMap, output)
    definition.namedStateMaps.entries.forEach { mapEntry ->
        makeStateMap(mapEntry.key, mapEntry.value, output)
    }
    return output.distinct()
}

private fun <A, C, E, R, S> makeStateMap(
    stateMapName: String,
    stateMap: StateMapDefinition<S, E, C, A, R>,
    output: MutableList<TransitionView>
) {
    stateMap.transitionRules.forEach { entry ->
        output += makeView(stateMapName, entry.key.first.toString(), entry.key.second.toString(), entry.value)
    }
    stateMap.transitionRules.entries.forEach { entry ->
        entry.value.guardedTransitions.forEach { transition ->
            output += makeView(stateMapName, entry.key.first.toString(), entry.key.second.toString(), transition)
        }
    }
    stateMap.defaultTransitions.forEach { entry ->
        output += makeView(stateMapName, null, entry.key.toString(), entry.value)
    }
    stateMap.automaticTransitions.forEach { entry ->
        output += makeView(stateMapName, entry.key.toString(), null, entry.value)
    }
}

fun <S, E, C, A, R> makeView(
    mapName: String,
    from: String?,
    event: String?,
    transition: Transition<S, E, C, A, R>?
): TransitionView {
    return when (transition) {
        is GuardedTransition<S, E, C, A, R> -> TransitionView(mapName, from.toString(), event.toString(), transition.targetState?.toString(), transition.targetMap, transition.action?.toString(), transition.automatic, transition.type, transition.guard.toString())
        is SimpleTransition<S, E, C, A, R>  -> TransitionView(mapName, from.toString(), event.toString(), transition.targetState?.toString(), transition.targetMap, transition.action?.toString(), transition.automatic, transition.type)
        is DefaultTransition<S, E, C, A, R> -> TransitionView(mapName, null, event.toString(), transition.targetState?.toString(), transition.targetMap, transition.action?.toString(), transition.automatic, DEFAULT)
        is Transition<S, E, C, A, R>        -> TransitionView(mapName, null, null, transition.targetState?.toString(), transition.targetMap, transition.action?.toString(), transition.automatic, transition.type)
        null                                -> TransitionView(mapName, from.toString(), event.toString())
        else                                -> error("Unknown Transition Type:$transition")
    }
}

fun <S, E, C, A, R> makeView(
    mapName: String,
    from: String?,
    event: String?,
    rules: TransitionRules<S, E, C, A, R>?
): TransitionView {
    return makeView(mapName, from?.toString(), event?.toString(), rules?.transition)
}

fun plantUml(input: Iterable<TransitionView>): String {
    val sw = StringWriter()
    val output = PrintWriter(sw)
    output.println("@startuml")
    val stateMaps = input.filter { it.sourceMap != null && it.sourceMap != "default" }.groupBy { it.sourceMap!! }
    stateMaps.forEach { sm ->
        val entry = input.filter { it.targetMap == sm.key }.firstOrNull()
        output.println("state ${entry?.targetMap} {")
        sm.value.forEach { transition ->
            output.print("  ");
            printPlantUmlTransition(transition, output)
        }
        output.println("}")

    }
    input.filter { it.sourceMap == "default" }.forEach { transition ->
        printPlantUmlTransition(transition, output)
    }
    output.println("@enduml")
    output.flush()
    sw.flush()
    return sw.toString()
}

private fun printPlantUmlTransition(transition: TransitionView, output: PrintWriter) {
    val startName =
        if (transition.type == DEFAULT) "default" else
            if ("<<start>>" == transition.start) "[*]" else transition.start
            ?: transition.target
    val endName = transition.target ?: transition.start
    val event = if (transition.automatic) "<<automatic>>" else transition.event
    if(event!=null) {
        output.println("$startName --> $endName : $event")
    } else {
        output.println("$startName --> $endName")
    }
}
