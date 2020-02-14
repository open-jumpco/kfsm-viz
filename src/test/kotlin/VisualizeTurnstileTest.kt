package io.jumpco.open.example.kfsm

import PacketReaderFSM
import io.jumpco.open.kfsm.PayingTurnstileFSM
import io.jumpco.open.kfsm.SecureTurnstileFSM
import io.jumpco.open.kfsm.viz.plantUml
import io.jumpco.open.kfsm.viz.visualize
import org.junit.Before
import org.junit.Test
import java.io.File

class VisualizeTurnstileTest {
    @Before
    fun setup() {
        val generated = File("generated")
        if (generated.exists() && !generated.isDirectory) {
            error("Expected generated to be a directory")
        } else if (!generated.exists()) {
            generated.mkdirs()
        }
    }

    @Test
    fun produceVisualizationTurnstileFSM() {
        println("== TurnStile")
        val visualisation =
            visualize(TurnstileFSM.definition)
        visualisation.forEach { v ->
            println("$v")
        }
        File("generated", "turnstile.plantuml").writeText(plantUml(visualisation))
    }

    @Test
    fun produceVisualizationPayingTurnstile() {
        println("== PayingTurnstile")
        val visualisation =
            visualize(PayingTurnstileFSM.definition)
        visualisation.forEach { v ->
            println("$v")
        }
        File("generated", "paying-turnstile.plantuml").writeText(plantUml(visualisation))
    }

    @Test
    fun produceVisualizationSecureTurnstile() {
        println("== SecureTurnstile")
        val visualisation =
            visualize(SecureTurnstileFSM.definition)
        visualisation.forEach { v ->
            println("$v")
        }
        File("generated", "secure-turnstile.plantuml").writeText(plantUml(visualisation))
    }

    @Test
    fun produceVisualizationPacketReader() {
        println("== PacketReader")
        val visualisation =
            visualize(PacketReaderFSM.definition)
        visualisation.forEach { v ->
            println("$v")
        }
        File("generated", "packet-reader.plantuml").writeText(plantUml(visualisation))
    }
}
