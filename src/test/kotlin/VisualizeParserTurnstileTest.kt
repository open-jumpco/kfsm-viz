package io.jumpco.open.example.kfsm

import PacketReaderFSM
import io.jumpco.open.kfsm.PayingTurnstileFSM
import io.jumpco.open.kfsm.SecureTurnstileFSM
import io.jumpco.open.kfsm.asciiDoc
import io.jumpco.open.kfsm.parseStateMachine
import io.jumpco.open.kfsm.plantUml
import io.jumpco.open.kfsm.visualize
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
        File("generated-parsed", "turnstile.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationPayingTurnstile() {
        println("== PayingTurnstile")
        val visualisation = parseStateMachine("PayingTurnstileFSM", File("src/test/kotlin/PayingTurnstile.kt"))
        println(visualisation)
        File("generated-parsed", "paying-turnstile.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "paying-turnstile.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationSecureTurnstile() {
        println("== SecureTurnstile")
        val visualisation = parseStateMachine("SecureTurnstileFSM", File("src/test/kotlin/SecureTurnstile.kt"))
        println(visualisation)
        File("generated-parsed", "secure-turnstile.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "secure-turnstile.adoc").writeText(asciiDoc(visualisation))
    }

    @Test
    fun produceVisualizationPacketReader() {
        println("== PacketReader")
        val visualisation = parseStateMachine("PacketReaderFSM", File("src/test/kotlin/PacketReader.kt"))
        println(visualisation)
        File("generated-parsed", "packet-reader.plantuml").writeText(plantUml(visualisation))
        File("generated-parsed", "packet-reader.adoc").writeText(asciiDoc(visualisation))
    }
}
