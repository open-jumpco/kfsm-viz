package io.jumpco.open.kfsm

import io.jumpco.open.kfsm.TransitionType.DEFAULT
import io.jumpco.open.kfsm.TransitionType.NORMAL
import java.io.PrintWriter
import java.io.StringWriter

private fun printPlantUmlTransition(transition: VisualTransition, output: PrintWriter) {
    val startName = if (transition.type == DEFAULT) transition.startMap else
        if ("<<start>>" == transition.start || "START" == transition.start) "[*]" else transition.start ?: "<<unknown>>"
    val endName = if ("<<end>>" == transition.target || "END" == transition.target) "[*]" else transition.target
        ?: transition.start ?: "<<unkown>>"
    val event = if (transition.automatic) "<<automatic>>" else transition.event ?: "<<unknown>>"
    output.print("$startName --> $endName")
    if (event != null) {
        output.print(" : $event")
    }
    if (transition.guard != null) {
        val guard = transition.guard!!.replace("\n", "\\l").replace("\r", "")
        output.print("\\l[$guard]")
    }

    if (transition.action != null && transition?.action?.trim() != "{}") {
        val action = transition?.action?.replace("\n", "\\l")?.replace("\r", "")
        output.print("\\l<<action>> $action")
    }
    output.println()
}

fun plantUml(statemachine: VisualStateMachineDefinion): String {
    val sw = StringWriter()
    val output = PrintWriter(sw)
    output.println("@startuml")
    output.println("skinparam StateFontName Helvetica")
    output.println("skinparam defaultFontName Monospaced")
    statemachine.stateMaps.filter { it.value.name != "default" }.forEach { stateMap ->
        output.println("state ${stateMap.value.name} {")
        stateMap.value.transitions.forEach {
            printPlantUmlTransition(it, output)
        }
        output.println("}")
    }
    statemachine.stateMaps.filter { it.value.name == "default" }.forEach { stateMap ->
        output.println("state ${stateMap.value.name} {")
        stateMap.value.transitions.forEach {
            printPlantUmlTransition(it, output)
        }
        output.println("}")
    }
    output.println("@enduml")
    output.flush()
    sw.flush()
    return sw.toString()
}

fun asciiDoc(statemachine: VisualStateMachineDefinion): String {
    val sw = StringWriter()
    val output = PrintWriter(sw)
    output.println("== ${statemachine.name} State Chart")
    output.println()

    statemachine.stateMaps.filter { it.value.name != "default" }.forEach { stateMap ->
        output.println("=== State Map ${stateMap.value.name}")
        output.println()
        output.println(
            """
        |===
        | Start | Event[Guard] | Target | Action
        """.trimIndent()
        )
        stateMap.value.transitions.forEach {
            printAsciiDocTransition(it, output)
        }
        output.println("|===")
        output.println()
    }
    statemachine.stateMaps.filter { it.value.name == "default" }.forEach { stateMap ->
        output.println("=== Default State Map")
        output.println()
        output.println(
            """
        |===
        | Start | Event[Guard] | Target | Action
        """.trimIndent()
        )
        stateMap.value.transitions.forEach {
            printAsciiDocTransition(it, output)
        }
        output.println("|===")
        output.println()
    }
    output.flush()
    sw.flush()
    return sw.toString()
}

fun escapeCharacters(input: String, escape: String): String {
    val builder = StringBuilder()
    input.forEach { c ->
        if (escape.contains(c)) {
            builder.append('\\')
        }
        builder.append(c)
    }
    return builder.toString()
}

fun printAsciiDocTransition(transition: VisualTransition, output: PrintWriter) {
    output.println()
    val startName = if (transition.type == DEFAULT) transition.startMap else
        if ("\\<<start>>" == transition.start || "START" == transition.start) "[*]" else transition.start
            ?: "\\<<unknown>>"
    val endName = if ("\\<<end>>" == transition.target || "END" == transition.target) "[*]" else transition.target
        ?: transition.start ?: "\\<<unkown>>"
    val event = if (transition.automatic) "\\<<automatic>>" else transition.event ?: "\\<<unknown>>"
    output.println("| $startName")
    if (event != null) {
        output.print("| $event")
    }
    if (transition.guard != null) {
        val guard = transition.guard!!.replace("\n", "").replace("\r", "")
        output.print(escapeCharacters(" `[$guard]`", "|"))
    }
    output.println()
    output.println("| $endName")
    output.print("| ")
    if (transition.action != null && transition?.action?.trim() != "{}") {
        val action = transition?.action?.replace("\n", "")?.replace("\r", "")
        output.print(escapeCharacters(" `$action`", "|"))
    }
    output.println()
}

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

fun <S, E, C, A, R> visualize(
    definition: StateMachineDefinition<S, E, C, A, R>
): Iterable<TransitionView> {
    val output = mutableListOf<TransitionView>()
    if (definition.defaultInitialState != null) {
        output.add(TransitionView("default", "<<start>>", null, definition.defaultInitialState.toString()))
    }
    val stateMap = definition.defaultStateMap
    makeStateMap(definition, "default", stateMap, output)
    definition.namedStateMaps.entries.forEach { mapEntry ->
        makeStateMap(definition, mapEntry.key, mapEntry.value, output)
    }
    return output.distinct()
}

private fun <A, C, E, R, S> makeStateMap(
    definition: StateMachineDefinition<S, E, C, A, R>,
    stateMapName: String,
    stateMap: StateMapDefinition<S, E, C, A, R>,
    output: MutableList<TransitionView>
) {
    val states = stateMap.validStates
    stateMap.transitionRules.forEach { entry ->
        makeView(definition, stateMapName, entry.key.first.toString(), entry.key.second, entry.value, output)
    }
    stateMap.transitionRules.entries.forEach { entry ->
        entry.value.guardedTransitions.forEach { transition ->
            makeView(definition, stateMapName, entry.key.first.toString(), entry.key.second, transition, output)
        }
    }
    stateMap.defaultTransitions.forEach { entry ->
        makeView(definition, stateMapName, null, entry.key, entry.value, output)
    }
    stateMap.automaticTransitions.forEach { entry ->
        makeView(definition, stateMapName, entry.key.toString(), null, entry.value, output)
    }
    stateMap.automaticTransitions.forEach { entry ->
        makeView(definition, stateMapName, entry.key.toString(), null, entry.value, output)
        entry.value.guardedTransitions.forEach { transition ->
            makeView(definition, stateMapName, entry.key.toString(), null, transition, output)
        }
    }
}

fun <S, E, C, A, R> makeView(
    definition: StateMachineDefinition<S, E, C, A, R>,
    mapName: String,
    from: String?,
    event: E?,
    transition: Transition<S, E, C, A, R>?,
    output: MutableList<TransitionView>
) {
    val mapDefinition = definition.namedStateMaps[mapName]
    val targetMap = transition?.targetMap
    val targetState = transition?.targetState
    // Ensure that target states are declared within their own map or default map not in a foreign named map.
    val sourceMap = if (mapDefinition != null && targetState != null) {
        if (mapDefinition.validStates.contains(targetState)) mapName else targetMap ?: "default"
    } else {
        mapName
    }
    val result = when (transition) {
        is GuardedTransition<S, E, C, A, R> -> TransitionView(sourceMap, from.toString(), event?.toString(), targetState?.toString(), targetMap, transition.action?.toString(), transition.automatic, transition.type, transition.guard.toString())
        is SimpleTransition<S, E, C, A, R>  -> TransitionView(sourceMap, from.toString(), event?.toString(), targetState?.toString(), targetMap, transition.action?.toString(), transition.automatic, transition.type)
        is DefaultTransition<S, E, C, A, R> -> TransitionView(sourceMap, null, event?.toString(), targetState?.toString(), targetMap, transition.action?.toString(), transition.automatic, DEFAULT)
        is Transition<S, E, C, A, R>        -> TransitionView(sourceMap, null, null, targetState?.toString(), targetMap, transition.action?.toString(), transition.automatic, transition.type)
        null                                -> TransitionView(sourceMap, from.toString(), event?.toString())
        else                                -> error("Unknown Transition Type:$transition")
    }
    output.add(result)
}

fun <S, E, C, A, R> makeView(
    definition: StateMachineDefinition<S, E, C, A, R>,
    mapName: String,
    from: String?,
    event: E?,
    rules: TransitionRules<S, E, C, A, R>?,
    output: MutableList<TransitionView>
) {
    makeView(definition, mapName, from?.toString(), event, rules?.transition, output)
}

fun plantUml(input: Iterable<TransitionView>): String {
    val sw = StringWriter()
    val output = PrintWriter(sw)
    output.println("@startuml")
    val stateMaps = input.filter { it.sourceMap != null && it.sourceMap != "default" }.groupBy { it.sourceMap!! }
    stateMaps.forEach { sm ->
        val entry = input.filter { it.sourceMap == sm.key }.firstOrNull()
        output.println("state ${entry?.sourceMap} {")
        sm.value.forEach { transition ->
            output.print("  ");
            printPlantUmlTransition(transition, output)
        }
        output.println("}")
    }
    output.println("state default {")
    input.filter { it.sourceMap == "default" }.forEach { transition ->
        output.print("  ");
        printPlantUmlTransition(transition, output)
    }
    output.println("}")
    output.println("@enduml")
    output.flush()
    sw.flush()
    return sw.toString()
}

private fun printPlantUmlTransition(transition: TransitionView, output: PrintWriter) {
    val startName =
        if (transition.type == DEFAULT) transition.sourceMap else
            if ("<<start>>" == transition.start) "[*]" else transition.start
                ?: transition.target
    val endName = transition.target ?: transition.start
    val event = if (transition.automatic) "<<automatic>>" else transition.event
    if (event != null) {
        output.println("$startName --> $endName : $event")
    } else {
        output.println("$startName --> $endName")
    }
}
