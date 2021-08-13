/*
 * Copyright (c) 2020-2021. Open JumpCO
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.jumpco.open.example.kfsm

import io.jumpco.open.kfsm.viz.Visualization.plantUml
import io.jumpco.open.kfsm.viz.Parser.parseStateMachine
import io.jumpco.open.kfsm.viz.Visualization.asciiDoc

import org.junit.Before
import org.junit.Test
import java.io.File

class VisualizeParserTurnstileTest {
    @Before
    fun setup() {
        val generated = File("generated-parsed")
        if (generated.exists() && !generated.isDirectory) {
            error("Expected generated to be a directory")
        } else if (!generated.exists()) {
            generated.mkdirs()
        }
    }

    @Test
    fun produceVisualizationTurnstileFSM() {
        println("== TurnStile")
        val visualisation = parseStateMachine("TurnstileFSM", File("src/test/kotlin/Turnstile.kt"))
        println(visualisation)
        File("generated-parsed", "turnstile.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "turnstile-simple.plantuml").writeText(plantUml(visualisation, false))
        File("generated-parsed", "turnstile.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationPayingTurnstile() {
        println("== PayingTurnstile")
        val visualisation = parseStateMachine("PayingTurnstileFSM", File("src/test/kotlin/PayingTurnstile.kt"))
        println(visualisation)
        File("generated-parsed", "paying-turnstile.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "paying-turnstile-simple.plantuml").writeText(plantUml(visualisation, false))
        File("generated-parsed", "paying-turnstile.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationSecureTurnstile() {
        println("== SecureTurnstile")
        val visualisation = parseStateMachine("SecureTurnstileFSM", File("src/test/kotlin/SecureTurnstile.kt"))
        println(visualisation)
        File("generated-parsed", "secure-turnstile.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "secure-turnstile-simple.plantuml").writeText(plantUml(visualisation, false))
        File("generated-parsed", "secure-turnstile.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationPacketReader() {
        println("== PacketReader")
        val visualisation = parseStateMachine("PacketReaderFSM", File("src/test/kotlin/PacketReader.kt"))
        println(visualisation)
        File("generated-parsed", "packet-reader.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "packet-reader-simple.plantuml").writeText(plantUml(visualisation, false))
        File("generated-parsed", "packet-reader.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationTimeoutSecureTurnstile() {
        println("== TimeoutSecureTurnstile")
        val visualisation =
            parseStateMachine("TimerSecureTurnstileFSM", File("src/test/kotlin/TimeoutSecureTurnstile.kt"))
        println(visualisation)
        File("generated-parsed", "timeout-secure-turnstile.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "timeout-secure-turnstile-simple.plantuml").writeText(plantUml(visualisation, false))
        File("generated-parsed", "timeout-secure-turnstile.adoc").writeText(asciiDoc(visualisation))
    }

}
