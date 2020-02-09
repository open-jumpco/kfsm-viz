package io.jumpco.open.kfsm

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

val spacesAround = setOf("equalityOperator", "comparisonOperator")
fun printTree(parseTree: KotlinParseTree): String {
    val builder = StringBuilder(0)
    when {
        parseTree.name == "semis" -> builder.append(";")
        parseTree.text != null    -> builder.append(parseTree.text)
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

fun isParent(parent: KotlinParseTree, child: KotlinParseTree): Boolean {
    return if (parent.equals(child)) {
        true
    } else {
        parent.children.any { isParent(it, child) }
    }
}

fun parserStateMap(name: String, stateMapTree: KotlinParseTree): VisualStateMapDefinition {
    val result = VisualStateMapDefinition(name)
    val stateMaps = stateMapTree.children.flatMap {
        findExpressionWithIdentifier("stateMap", it)
    }
    val whenStates = stateMapTree.children.flatMap {
        findExpressionWithIdentifier("whenState", it)
    }.filterNot { ws -> stateMaps.any { isParent(it, ws) } }
    whenStates.forEach { state ->
        var stateName = printTree(findNodeByType("valueArgument", state).toList().first())
        if (stateName.contains('.')) {
            stateName = stateName.substringAfter(".")
        }
        // println("whenState:$stateName:${printTree(state)}")
        result.states += stateName
        val onEvents = state.children.flatMap {
            findExpressionWithIdentifier(stateMachineEventMethodNames, it)
        }
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
        // println("action:$stateName:$onEventText:${printAllTree(actionLambda)}")
        result.action = printTree(actionLambda)
    }
//    println("$onEventText:${printAllTree(valueArg)}")
    val valueArgs = valueArg.children.filter { it.name == "valueArgument" }
    val stringArgs = mutableListOf<String>()
    valueArgs.forEach { child ->
        val identifiers = findNodeByType("Identifier", child).toList()
        val toId = identifiers.find { it.text == "to" }
        if (toId != null) {
            val rangeExp = findNodeByType("rangeExpression", child)
                .map { printTree(it) }
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
    // println("$onEventText:$stringArgs")
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
    } else {
        val children = parseTree.children.flatMap { findNodeByType(type, it) }
        result += children
    }
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

fun findNodeWithTypeAndWithIdentifier(
    type: String,
    identifier: Set<String>,
    parseTree: KotlinParseTree
): Iterable<KotlinParseTree> {
    val identifierNodes =
        findNodeByType(type, parseTree)
            .filter { findChildWithName("Identifier", identifier, it).toList().isNotEmpty() }
    return identifierNodes
}

fun findNodeWithTypeAndWithIdentifier(
    type: String,
    identifier: String,
    parseTree: KotlinParseTree
): Iterable<KotlinParseTree> {
    val identifierNodes =
        findNodeByType(type, parseTree)
            .filter { findChildWithName("Identifier", identifier, it).toList().isNotEmpty() }
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