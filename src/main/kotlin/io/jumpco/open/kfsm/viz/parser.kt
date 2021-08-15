/*
 * Copyright (c) 2020-2021. Open JumpCO
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.jumpco.open.kfsm.viz

import io.jumpco.open.kfsm.viz.TransitionType.*
import org.jetbrains.kotlin.spec.grammar.tools.KotlinParseTree
import org.jetbrains.kotlin.spec.grammar.tools.KotlinParseTreeNodeType
import org.jetbrains.kotlin.spec.grammar.tools.parseKotlinCode
import org.jetbrains.kotlin.spec.grammar.tools.tokenizeKotlinCode
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.exp

/**
 * @author Corneil du Plessis
 * @soundtrack Wolfgang Amadeus Mozart
 */
enum class TransitionType {
    /**
     * Transitions are triggered and may change to a new state or remain at the same state while performing an action.
     */
    NORMAL,

    /**
     * A push transition will place the current state map on a stack and make the named statemap the current map and change to the given state,
     */
    PUSH,

    /**
     * A pop transition will pop the stack and make the transition current. If the pop transition provided a new targetMap or targetState that will result in push or normal transition behaviour.
     */
    POP,

    /**
     * A default transition will take place when no configured state/event pair matches.
     */
    DEFAULT
}

internal val stateMachineEventMethodNames =
    setOf(
        "onEvent",
        "automatic",
        "automaticPop",
        "onEventPush",
        "onEventPop",
        "automaticPush",
        "timeout",
        "timeoutPop",
        "timeoutPush"
    )

internal val stateMachineCreatorMethodNames =
    setOf("stateMachine", "asyncStateMachine", "functionalStateMachine", "asyncFunctionalStateMachine")

class VisualStateMachineDefinion(val name: String) {
    val invariants: MutableSet<String> = mutableSetOf()
    val stateMaps: MutableMap<String, VisualStateMapDefinition> = mutableMapOf()
    override fun toString(): String {
        return """VisualStateMachineDefinion(name='$name', 
invariants=${invariants.joinToString("\n")}, .
stateMaps=${stateMaps.values.joinToString("\n")})"""
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
    var timeout: String? = null,
    var type: TransitionType = TransitionType.NORMAL,
    var guard: String? = null
)

class VisualStateMapDefinition(val name: String) {
    val states: MutableSet<String> = mutableSetOf()
    val events: MutableSet<String> = mutableSetOf()
    val transitions: MutableList<VisualTransition> = mutableListOf()
    override fun toString(): String {
        return "VisualStateMapDefinition(name='$name', states=$states, events=$events, transitions=${
            transitions.joinToString(
                "\n"
            )
        })"
    }
}

object Parser {
    private fun printTreeDepth(parseTree: KotlinParseTree, writer: PrintWriter, depth: Int = 0) {
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

    private fun stripQuotes(input: String): String {
        return (if (input.startsWith("\"")) input.substring(1) else input).let {
            if (it.endsWith("\"")) {
                it.substring(0 until it.length - 1)
            } else {
                it
            }
        }
    }

    private fun printAllTree(parseTree: KotlinParseTree): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        printTreeDepth(parseTree, pw)
        pw.flush()
        sw.flush()
        return sw.toString()
    }

    private val spacesAround = setOf("equalityOperator", "comparisonOperator")
    private fun printTree(parseTree: KotlinParseTree, includeSemis: Boolean = true): String {
        val builder = StringBuilder(0)
        when {
            parseTree.name == "semis" -> if (includeSemis) builder.append(';') else builder.append("")
            parseTree.text != null -> builder.append(parseTree.text)
        }
        if (spacesAround.contains(parseTree.name)) {
            builder.append(' ')
        }
        parseTree.children.forEach {
            builder.append(printTree(it, includeSemis))
        }
        if (spacesAround.contains(parseTree.name)) {
            builder.append(' ')
        }
        return builder.toString()
    }

    @JvmStatic
    fun parseStateMachine(parentClass: String, sourceFile: File): VisualStateMachineDefinion {
        val tokens = tokenizeKotlinCode(sourceFile.readText())
        val parseTree = parseKotlinCode(tokens)
        val classNode = findClass(parentClass, parseTree)
        // TODO search for whenState and find parent function
        val stateMachine = findExpressionWithIdentifier(stateMachineCreatorMethodNames, classNode).first()
        val result = VisualStateMachineDefinion(parentClass)
        val invariants = stateMachine.children.flatMap {
            findExpressionWithIdentifier("invariant", it)
        }.mapNotNull { invariant ->
            val functionDecl = findNodeByType("callSuffix", invariant).first()
            val actionLambda = functionDecl.children.find { it.name == "annotatedLambda" }
            if (actionLambda != null) {
                printTree(actionLambda, false)
            } else {
                null
            }
        }
        result.invariants.addAll(invariants)
        val stateMaps = stateMachine.children.flatMap {
            findExpressionWithIdentifier("stateMap", it)
        }.map { stateMap ->
            val name = stripQuotes(printTree(findNodeByType("valueArgument", stateMap).first()))
            parserStateMap(name, stateMap)
        }.associateBy { it.name }
        result.stateMaps += stateMaps
        result.stateMaps[parentClass] =
            parserStateMap(parentClass, stateMachine)
        val states = result.stateMaps.values.flatMap { it.states }.toSet()
        val hasStart = result.stateMaps.values.flatMap { it.transitions }.filter { "<<start>>" == it.start || "START" == it.start }.isNotEmpty()
        if(!hasStart) {
            val startState = stateMachine.children.flatMap {
                findExpressionWithIdentifier("initialState", it)
            }.mapNotNull {
                print("initialState=")
                println(printTree(it))
                val values = findNodeWithText(
                    KotlinParseTreeNodeType.TERMINAL,
                    "Identifier",
                    it
                ).filter { it.text != "initialState" }.toList()
                if (values.isNotEmpty()) {
                    val expr = stripQuotes(printTree(values.last()))
                    val state = if (expr.contains('.')) {
                        expr.substringAfterLast(".")
                    } else {
                        expr
                    }
                    if (states.contains(state)) {
                        state
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            if (startState.isNotEmpty()) {
                val startStateName = startState.first()!!
                val stateMachineDefinition = result.stateMaps[result.name]!!
                if (stateMachineDefinition.states.contains(startStateName)) {
                    stateMachineDefinition.transitions.add(VisualTransition("<<start>>", null, startStateName))
                } else {
                    println("$startState isn't a known state")
                }
            }
        }
        return result
    }

    private fun isParent(parent: KotlinParseTree, child: KotlinParseTree): Boolean {
        return if (parent.equals(child)) {
            true
        } else {
            parent.children.any { isParent(it, child) }
        }
    }

    private fun parserStateMap(name: String, stateMapTree: KotlinParseTree): VisualStateMapDefinition {
        val result = VisualStateMapDefinition(name)
        val stateMaps = stateMapTree.children.flatMap {
            findExpressionWithIdentifier("stateMap", it)
        }
        val whenStates = stateMapTree.children.flatMap {
            findExpressionWithIdentifier("whenState", it)
        }.filterNot { ws ->
            stateMaps.any {
                isParent(it, ws)
            }
        }
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
            val transitions = onEvents.map {
                parseStateTransition(stateName, it)
            }
            result.events.addAll(transitions.mapNotNull { it.event })
            result.transitions.addAll(transitions)
        }
        return result
    }

    private fun parseStateTransition(stateName: String, parseTree: KotlinParseTree): VisualTransition {
        val result = VisualTransition(stripQuotes(stateName))
        // println("parseStateTransition:\n${parseTree}\n")
        val onEventExp = findNodeByType("Identifier", parseTree).first()
        val onEventText = onEventExp.text ?: error("Expected name from $onEventExp")
        val functionDecl = findNodeByType("callSuffix", parseTree).first()
        val valueArg = findNodeByType("valueArguments", functionDecl).toList().first()
        val actionLambda = functionDecl.children.find { it.name == "annotatedLambda" }
        if (actionLambda != null) {
            // println("action:$stateName:$onEventText:${printAllTree(actionLambda)}")
            result.action = printTree(actionLambda, false)
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
                val str = printTree(child, true).trim()
                if (!str.startsWith("guard")) {
                    stringArgs += str
                } else {
                    result.guard = str.substringAfter("guard=")
                }
            }
        }
        // println("$onEventText:$stringArgs")
        when {
            onEventText == "onEvent" -> {
                val toIdx = stringArgs.indexOf("to")
                if (toIdx > 0) {
                    result.event = stringArgs[toIdx - 1]
                    result.target = stringArgs[toIdx + 1]
                } else {
                    result.event = stringArgs[0]
                }
                result.type = NORMAL
            }
            onEventText == "automatic" -> {
                when {
                    stringArgs.size == 1 -> {
                        result.target = stringArgs[0]
                    }
                    else -> {
                        error("Unexpected number of arguments for automaticPop:$stringArgs")
                    }
                }
                result.automatic = true
                result.type = NORMAL
            }
            onEventText == "automaticPop" -> {
                when {
                    stringArgs.size == 2 -> {
                        result.targetMap = stringArgs[0]
                        result.target = stringArgs[1]
                    }
                    stringArgs.size == 1 -> {
                        result.target = stringArgs[0]
                    }
                    else -> {
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
            onEventText == "timeout" -> {
                when {
                    stringArgs.size == 1 -> {
                        result.target = stringArgs[0]
                    }
                    stringArgs.size == 2 -> {
                        result.target = stringArgs[0]
                        result.timeout = stringArgs[1]
                    }
                    else -> {
                        error("Unexpected number of arguments for timeout:$stringArgs")
                    }
                }
                result.automatic = false
                result.type = NORMAL
                result.event = "<<timeout = ${result.timeout}>>"
            }
            onEventText == "timeoutPop" -> {
                when {
                    stringArgs.size == 3 -> {
                        result.targetMap = stringArgs[0]
                        result.target = stringArgs[1]
                        result.timeout = stringArgs[2]
                    }
                    stringArgs.size == 2 -> {
                        result.target = stringArgs[0]
                        result.timeout = stringArgs[1]
                    }
                    else -> {
                        error("Unexpected number of arguments for automaticPop:$stringArgs")
                    }
                }
                result.automatic = false
                result.type = POP
                result.event = "<<timeout = ${result.timeout}>>"
            }
            onEventText == "timeoutPush" -> {

                result.targetMap = stringArgs[0]
                result.target = stringArgs[1]
                result.timeout = stringArgs[2]
                result.automatic = false
                result.type = PUSH
                result.event = "<<timeout = ${result.timeout}>>"
            }

            onEventText == "onEventPop" -> {
                when (stringArgs.size) {
                    1 -> {
                        result.event = stringArgs[0]
                    }
                    2 -> {
                        result.event = stringArgs[0]
                        result.target = stringArgs[1]
                    }
                    3 -> {
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
            onEventText == "onEventPush" -> {
                when (stringArgs.size) {
                    3 -> {
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
        if (result.event?.contains('.') ?: false) {
            result.event = result.event?.substringAfter(".")
        }
        if (result.target?.contains('.') ?: false) {
            result.target = result.target?.substringAfter(".")
        }
        return result
    }

    private fun parseLongLiteral(input: String): Long {
        val strValue = if (input.endsWith("L")) {
            input.substringBefore("L")
        } else {
            input
        }
        return strValue.toLong()
    }

    private fun findNodeWithText(
        type: KotlinParseTreeNodeType,
        name: String,
        parseTree: KotlinParseTree
    ): Iterable<KotlinParseTree> {
        val result = mutableListOf<KotlinParseTree>()
        if (parseTree.type == type && parseTree.text != null && parseTree.name == name) {
            result += parseTree
        }
        result += parseTree.children.flatMap {
            findNodeWithText(type, name, it)
        }
        return result
    }

    private fun findNodeByType(type: String, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
        val result = mutableListOf<KotlinParseTree>()
        if (parseTree.name == type) {
            result.add(parseTree)
        } else {
            val children = parseTree.children.flatMap {
                findNodeByType(type, it)
            }
            result += children
        }
        return result
    }

    private fun findChildWithName(name: String, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
        val result = mutableListOf<KotlinParseTree>()
        if (parseTree.name == name) {
            result.add(parseTree)
        }
        result += parseTree.children.flatMap {
            findChildWithName(name, it)
        }
        return result
    }

    private fun findChildWithName(name: String, type: String, parseTree: KotlinParseTree): Iterable<KotlinParseTree> {
        val result = mutableListOf<KotlinParseTree>()
        if (parseTree.name == name && parseTree.text == type) {
            result.add(parseTree)
        }
        result += parseTree.children.flatMap {
            findChildWithName(name, type, it)
        }
        return result
    }

    private fun findChildWithName(
        name: String,
        type: Set<String>,
        parseTree: KotlinParseTree
    ): Iterable<KotlinParseTree> {
        val result = mutableListOf<KotlinParseTree>()
        if (parseTree.name == name && type.contains(parseTree.text)) {
            result.add(parseTree)
        }
        result += parseTree.children.flatMap {
            findChildWithName(name, type, it)
        }
        return result
    }

    private fun findNodeWithTypeAndWithIdentifier(
        type: String,
        identifier: Set<String>,
        parseTree: KotlinParseTree
    ): Iterable<KotlinParseTree> {
        val identifierNodes =
            findNodeByType(type, parseTree)
                .filter { findChildWithName("Identifier", identifier, it).toList().isNotEmpty() }
        return identifierNodes
    }

    private fun findNodeWithTypeAndWithIdentifier(
        type: String,
        identifier: String,
        parseTree: KotlinParseTree
    ): Iterable<KotlinParseTree> {
        val identifierNodes =
            findNodeByType(type, parseTree)
                .filter { findChildWithName("Identifier", identifier, it).toList().isNotEmpty() }
        return identifierNodes
    }

    private fun findExpressionWithIdentifier(
        identifier: String,
        parseTree: KotlinParseTree
    ): Iterable<KotlinParseTree> {
        return findNodeWithTypeAndWithIdentifier("expression", identifier, parseTree)
    }

    private fun findExpressionWithIdentifier(
        identifier: Set<String>,
        parseTree: KotlinParseTree
    ): Iterable<KotlinParseTree> {
        return findNodeWithTypeAndWithIdentifier("expression", identifier, parseTree)
    }

    private fun findClass(className: String, parseTree: KotlinParseTree): KotlinParseTree {
        val classNode = findNodeWithTypeAndWithIdentifier("classDeclaration", className, parseTree).toList()
        require(classNode.isNotEmpty()) { "Expected to find classDeclaration for $className in $parseTree" }
        return classNode.first()
    }
}
