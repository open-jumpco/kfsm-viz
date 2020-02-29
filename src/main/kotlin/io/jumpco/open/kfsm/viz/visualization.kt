/*
 * Copyright (c) 2020. Open JumpCO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.jumpco.open.kfsm.viz



import io.jumpco.open.kfsm.viz.TransitionType.*

import java.io.PrintWriter
import java.io.StringWriter

object Visualization {
    private fun printPlantUmlTransition(transition: VisualTransition, output: PrintWriter) {
        val startName = if (transition.type == DEFAULT) transition.startMap else
            if ("<<start>>" == transition.start || "START" == transition.start) "[*]" else transition.start
                ?: "<<unknown>>"
        val endName = if ("<<end>>" == transition.target || "END" == transition.target) "[*]" else transition.target
            ?: transition.start ?: "<<unkown>>"
        val event = if (transition.automatic) "<<automatic>>" else transition.event
        output.print("$startName --> $endName")
        if (event != null) {
            output.print(" : $event")
        }
        if (transition.guard != null) {
            val guard = transition.guard!!.replace("\n", "\\l").replace("\r", "")
            output.print(" [$guard]")
        }

        if (transition.action != null && transition.action?.trim() != "{}") {
            val action = transition.action?.replace("\n", "\\l")?.replace("\r", "")
            output.print("\\l<<action>> $action")
        }
        output.println()
    }

    @JvmStatic
    public fun plantUml(statemachine: VisualStateMachineDefinion): String {
        val sw = StringWriter()
        val output = PrintWriter(sw)
        output.println("@startuml")
        output.println("skinparam StateFontName Helvetica")
        output.println("skinparam defaultFontName Monospaced")
        output.println(
            """
            skinparam state {
                BackgroundColor LightBlue
            }
        """.trimIndent()
        )
        statemachine.stateMaps.filter { it.value.name != statemachine.name }.forEach { stateMap ->
            output.println("state ${stateMap.value.name} {")
            stateMap.value.transitions.forEach {
                printPlantUmlTransition(it, output)
            }
            output.println("}")
        }
        statemachine.stateMaps.filter { it.value.name == statemachine.name }.forEach { stateMap ->
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

    @JvmStatic
    public fun asciiDoc(statemachine: VisualStateMachineDefinion): String {
        val sw = StringWriter()
        val output = PrintWriter(sw)
        output.println("== ${statemachine.name} State Chart")
        output.println()

        statemachine.stateMaps.filter { it.value.name != statemachine.name }.forEach { stateMap ->
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
        statemachine.stateMaps.filter { it.value.name == statemachine.name }.forEach { stateMap ->
            output.println("=== ${statemachine.name} State Map")
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
        val event = if (transition.automatic) "\\<<automatic>>" else transition.event?.replace("<<","\\<<") ?: "\\<<unknown>>"
        output.println("| $startName")
        output.print("| $event")

        if (transition.guard != null) {
            val guard = transition.guard?.replace("\n", "")?.replace("\r", "")
            output.print(escapeCharacters(" `[$guard]`", "|"))
        }
        output.println()
        output.println("| $endName")
        output.print("a| ")
        if (transition.action != null && transition.action?.trim() != "{}") {
            val action = transition.action?.replace("\r", "")
            output.print(escapeCharacters("""[source,kotlin]
----
$action
----""", "|"))
        }
        output.println()
    }
}

