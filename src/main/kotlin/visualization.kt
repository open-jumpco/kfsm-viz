package io.jumpco.open.kfsm

import io.jumpco.open.kfsm.TransitionType.DEFAULT
import io.jumpco.open.kfsm.TransitionType.NORMAL
import io.jumpco.open.kfsm.TransitionType.POP
import io.jumpco.open.kfsm.TransitionType.PUSH
import org.jetbrains.kotlin.spec.grammar.tools.KotlinParseTree
import org.jetbrains.kotlin.spec.grammar.tools.parseKotlinCode
import org.jetbrains.kotlin.spec.grammar.tools.tokenizeKotlinCode
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

internal val stateMachineEventMethodNames =
    setOf("onEvent", "automatic", "automaticPop", "onEventPush", "onEventPop", "automaticPush")

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

class VisualStateMachineDefinion(val name: String) {
    val stateMaps: MutableMap<String, VisualStateMapDefinition> = mutableMapOf()
    override fun toString(): String {
        return "VisualStateMachineDefinion(name='$name', stateMaps=${stateMaps.values.joinToString("\n")})"
    }

}

data class VisualTransition(
    val start: String?,
    var event: String? = null,
    var target: String? = null,
    var startMap: String? = null,
    var targetMap: String? = null,
    var action: String? = null,
    var automatic: Boolean = false,
    var type: TransitionType = NORMAL,
    var guard: String? = null
)

class VisualStateMapDefinition(val name: String) {
    val states: MutableSet<String> = mutableSetOf()
    val events: MutableSet<String> = mutableSetOf()
    val transitions: MutableList<VisualTransition> = mutableListOf()
    override fun toString(): String {
        return "VisualStateMapDefinition(name='$name', states=$states, events=$events, transitions=${transitions.joinToString("\n")})"
    }

}

fun printTreeDepth(parseTree: KotlinParseTree, writer: PrintWriter, depth: Int = 0) {
    repeat(depth) { writer.print("  ") }
    writer.print(parseTree.name)
    if (parseTree.text != null) {
        writer.println("=${parseTree.text}")
    } else {
        writer.println()
    }
    parseTree.children.forEach {
        printTreeDepth(it, writer, depth + 1)
    }
}

fun stripQuotes(input: String): String {
    return (if (input.startsWith("\"")) input.substring(1) else input).let {
        if (it.endsWith("\"")) {
            it.substring(0 until it.length - 1)
        } else {
            it
        }
    }
}

fun printAllTree(parseTree: KotlinParseTree): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    printTreeDepth(parseTree, pw)
    pw.flush()
    sw.flush()
    return sw.toString()
}

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
        val guard = transition.guard!!.replace("\n", "").replace("\r", "")
        output.print("[$guard]")
    }

    if (transition.action != null && transition?.action?.trim() != "{}") {
        val action = transition?.action?.replace("\n", " ")?.replace("\r", " ")
        output.print(" :: $action")
    }
    output.println()
}

fun plantUml(statemachine: VisualStateMachineDefinion): String {
    val sw = StringWriter()
    val output = PrintWriter(sw)
    output.println("@startuml")
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
    output.println("= ${statemachine.name} State Chart")
    output.println()

    statemachine.stateMaps.filter { it.value.name != "default" }.forEach { stateMap ->
        output.println("== State Map ${stateMap.value.name}")
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
        output.println("== Default State Map")
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
        val action = transition?.action?.replace("\n", " ")?.replace("\r", " ")
        output.print(escapeCharacters(" `$action`", "|"))
    }
    output.println()
}

val spacesAround = setOf("equalityOperator", "comparisonOperator")
fun printTree(parseTree: KotlinParseTree): String {
    val builder = StringBuilder(0)

    if (parseTree.text != null) {
        builder.append(parseTree.text)
    }
    if (spacesAround.contains(parseTree.name)) {
        builder.append(' ')
    }
    parseTree.children.forEach {
        builder.append(printTree(it))
    }
    if (spacesAround.contains(parseTree.name)) {
        builder.append(' ')
    }
    return builder.toString()
}

fun parseStateMachine(parentClass: String, sourceFile: File): VisualStateMachineDefinion {
    val tokens = tokenizeKotlinCode(sourceFile.readText())
    val parseTree = parseKotlinCode(tokens)
    val classNode = findClass(parentClass, parseTree)
    val stateMachine = findExpressionWithIdentifier("stateMachine", classNode).first()
    val result = VisualStateMachineDefinion(parentClass)
    val stateMaps = stateMachine.children.flatMap {
        findExpressionWithIdentifier("stateMap", it)
    }.map { stateMap ->
        val name = stripQuotes(printTree(findNodeByType("valueArgument", stateMap).first()))
        parserStateMap(name, stateMap)
    }.associateBy { it.name }
    result.stateMaps += stateMaps
    result.stateMaps["default"] = parserStateMap("default", stateMachine)
    return result
}

fun parserStateMap(name: String, stateMapTree: KotlinParseTree): VisualStateMapDefinition {
    val result = VisualStateMapDefinition(name)
    // TODO find all expression nodes either stateMap or whenState stop when successful
    val search = stateMapTree.children.filterNot { findExpressionWithIdentifier("stateMap", it).toList().isNotEmpty() }
    search.forEach {
        println("search:$name:${printAllTree(it)}")
    }
    val whenStates = (if (name == "default") stateMapTree.children else search).flatMap {
        findExpressionWithIdentifier("whenState", it)
    }
    whenStates.forEach { state ->
        var stateName = printTree(findNodeByType("valueArgument", state).toList().first())
        if (stateName.contains('.')) {
            stateName = stateName.substringAfter(".")
        }
        result.states += stateName
        val onEvents = state.children.flatMap { findExpressionWithIdentifier(stateMachineEventMethodNames, it) }
        val transitions = onEvents.map { parseStateTransition(stateName, it) }
        result.events.addAll(transitions.mapNotNull { it.event })
        result.transitions.addAll(transitions)
    }
    return result
}

fun parseStateTransition(stateName: String, parseTree: KotlinParseTree): VisualTransition {
    val result = VisualTransition(stripQuotes(stateName))
    // println("parseStateTransition:\n${parseTree}\n")
    val onEventExp = findNodeByType("Identifier", parseTree).first()
    val onEventText = onEventExp.text ?: error("Expected name from ${onEventExp}")
    val functionDecl = findNodeByType("callSuffix", parseTree).first()
    val valueArg = findNodeByType("valueArguments", functionDecl).toList().first()
    val actionLambda = functionDecl.children.find { it.name == "annotatedLambda" }
    if (actionLambda != null) {
        result.action = printTree(actionLambda)
    }
//    println("$onEventText:${printAllTree(valueArg)}")
    val valueArgs = valueArg.children.filter { it.name == "valueArgument" }
    val stringArgs = mutableListOf<String>()
    valueArgs.forEach { child ->
        val identifiers = findNodeByType("Identifier", child).toList()
        val toId = identifiers.find { it.text == "to" }
        if (toId != null) {
            val rangeExp = findNodeByType("rangeExpression", child).map { printTree(it) }
            stringArgs += rangeExp.first().trim()
            stringArgs += "to"
            stringArgs += rangeExp.last().trim()
        } else {
            val str = printTree(child).trim()
            if (!str.startsWith("guard")) {
                stringArgs += str
            } else {
                result.guard = str.substringAfter("guard=")
            }
        }
    }
    println("$onEventText:$stringArgs")
    when {
        onEventText == "onEvent"       -> {
            val toIdx = stringArgs.indexOf("to")
            if (toIdx > 0) {
                result.event = stringArgs[toIdx - 1]
                result.target = stringArgs[toIdx + 1]
            } else {
                result.event = stringArgs[0]
            }
            result.type = NORMAL
        }
        onEventText == "automatic"     -> {
            when {
                stringArgs.size == 1 -> {
                    result.target = stringArgs[0]
                }
                else                 -> {
                    error("Unexpected number of arguments for automaticPop:$stringArgs")
                }
            }
            result.automatic = true
            result.type = NORMAL
        }
        onEventText == "automaticPop"  -> {
            when {
                stringArgs.size == 2 -> {
                    result.targetMap = stringArgs[0]
                    result.target = stringArgs[1]
                }
                stringArgs.size == 1 -> {
                    result.target = stringArgs[0]
                }
                else                 -> {
                    error("Unexpected number of arguments for automaticPop:$stringArgs")
                }
            }
            result.automatic = true
            result.type = POP
        }
        onEventText == "automaticPush" -> {
            result.event = "<<automatic>>"
            require(stringArgs.size == 2) { "Expected automaticPush to have 2 arguments not $stringArgs" }
            result.targetMap = stringArgs[0]
            result.target = stringArgs[1]
            result.automatic = true
            result.type = PUSH
        }
        onEventText == "onEventPop"    -> {
            when (stringArgs.size) {
                1    -> {
                    result.event = stringArgs[1]
                }
                2    -> {
                    result.event = stringArgs[0]
                    result.target = stringArgs[1]
                }
                3    -> {
                    result.event = stringArgs[0]
                    result.targetMap = stringArgs[1]
                    result.target = stringArgs[2]
                }
                else -> {
                    error("Unexpected number of arguments for onEventPop:$stringArgs")
                }
            }
            result.type = POP
        }
        onEventText == "onEventPush"   -> {
            when (stringArgs.size) {
                3    -> {
                    result.event = stringArgs[0]
                    result.targetMap = stringArgs[1]
                    result.target = stringArgs[2]
                }
                else -> {
                    error("Unexpected number of arguments for onEventPush:$stringArgs")
                }
            }
            result.type = PUSH
        }
    }
    if (result?.event?.contains('.') ?: false) {
        result.event = result?.event?.substringAfter(".")
    }
    if (result?.target?.contains('.') ?: false) {
        result.target = result?.target?.substringAfter(".")
    }
    return result
}

fun findNodeByType(type: String, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
    val result = mutableListOf<KotlinParseTree>()
    if (parseTree.name == type) {
        result.add(parseTree)
    }
    val children = parseTree.children.flatMap { findNodeByType(type, it) }
    result += children
    return result
}

fun findChildWithName(name: String, type: String, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
    val result = mutableListOf<KotlinParseTree>()
    if (parseTree.name == name && parseTree.text == type) {
        result.add(parseTree)
    }
    result += parseTree.children.flatMap { findChildWithName(name, type, it) }
    return result
}

fun findChildWithName(name: String, type: Set<String>, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
    val result = mutableListOf<KotlinParseTree>()
    if (parseTree.name == name && type.contains(parseTree.text)) {
        result.add(parseTree)
    }
    result += parseTree.children.flatMap { findChildWithName(name, type, it) }
    return result
}

fun findNodeWithTypeAndName(type: String, name: String, parseTree: KotlinParseTree): KotlinParseTree? {
    if (parseTree.name == type && parseTree.text == name) return parseTree
    return parseTree.children.find { findNodeWithTypeAndName(type, name, it) != null }
}

fun findNodeWithTypeAndWithIdentifier(
    type: String,
    identifier: Set<String>,
    parseTree: KotlinParseTree
): Iterable<KotlinParseTree> {
    val identifierNodes =
        findNodeByType(type, parseTree).filter { findChildWithName("Identifier", identifier, it).toList().isNotEmpty() }
    return identifierNodes
}

fun findNodeWithTypeAndWithIdentifier(
    type: String,
    identifier: String,
    parseTree: KotlinParseTree
): Iterable<KotlinParseTree> {
    val identifierNodes =
        findNodeByType(type, parseTree).filter { findChildWithName("Identifier", identifier, it).toList().isNotEmpty() }
    return identifierNodes
}

fun findExpressionWithIdentifier(identifier: String, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
    return findNodeWithTypeAndWithIdentifier("expression", identifier, parseTree)
}

fun findExpressionWithIdentifier(identifier: Set<String>, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
    return findNodeWithTypeAndWithIdentifier("expression", identifier, parseTree)
}

fun findClass(className: String, parseTree: KotlinParseTree): KotlinParseTree {
    val classNode = findNodeWithTypeAndWithIdentifier("classDeclaration", className, parseTree)
    return classNode.first()
}

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
