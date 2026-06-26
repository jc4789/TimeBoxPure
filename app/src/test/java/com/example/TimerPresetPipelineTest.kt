package com.example

import com.example.timeboxvibe.engine.getDefaultPresets
import com.example.timeboxvibe.engine.core.TimerEngine
import com.example.timeboxvibe.engine.core.TimerPreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPresetPipelineTest {
    @Test
    fun downSpiralSkipAndDismissAdvanceToSecondStage() {
        val preset = preset("down_spiral")
        val skipped = TimerEngine(preset)
        assertEquals(0, skipped.currentIndex)
        assertEquals(600, skipped.timeRemaining)

        skipped.skip()
        assertEquals(1, skipped.currentIndex)
        assertEquals(540, skipped.timeRemaining)
        assertEquals(540, skipped.totalDuration)

        val dismissed = TimerEngine(preset)
        ringCurrentStage(dismissed)
        dismissed.dismissAlarm()
        assertEquals(1, dismissed.currentIndex)
        assertEquals(540, dismissed.timeRemaining)
        assertEquals(540, dismissed.totalDuration)
    }

    @Test
    fun upClimbDismissAdvancesToSecondStage() {
        val engine = TimerEngine(preset("up_climb"))
        assertEquals(0, engine.currentIndex)
        assertEquals(60, engine.timeRemaining)

        ringCurrentStage(engine)
        engine.dismissAlarm()
        assertEquals(1, engine.currentIndex)
        assertEquals(120, engine.timeRemaining)
    }

    @Test
    fun vibeWaveDismissAdvancesToSecondStage() {
        val engine = TimerEngine(preset("vibe_wave"))
        assertEquals(0, engine.currentIndex)
        assertEquals(60, engine.timeRemaining)

        ringCurrentStage(engine)
        engine.dismissAlarm()
        assertEquals(1, engine.currentIndex)
        assertEquals(180, engine.timeRemaining)
    }

    @Test
    fun defaultCalendarDismissAdvancesToShortBreak() {
        val engine = TimerEngine(preset("default_calendar"))
        assertEquals(0, engine.currentIndex)
        assertEquals("Focus Session 1", engine.currentStageLabel)
        assertEquals(1500, engine.timeRemaining)

        ringCurrentStage(engine)
        engine.dismissAlarm()
        assertEquals(1, engine.currentIndex)
        assertEquals("Short Break", engine.currentStageLabel)
        assertEquals("relax", engine.currentStageType)
        assertEquals(300, engine.timeRemaining)
    }

    @Test
    fun sequenceTemplateSaveLoadKeepsAllStagesAndDismissesForward() {
        val template = TimerPreset(
            id = "custom_1",
            name = "Three Stage",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180),
            alarmBehavior = "alarm"
        ).normalized(logFailures = true)
        val loaded = roundTrip(template)

        assertEquals("sequence", loaded.mode)
        assertEquals(3, loaded.sequence.size)
        assertEquals(60, loaded.sequence[0])
        assertEquals(120, loaded.sequence[1])
        assertEquals(180, loaded.sequence[2])

        val engine = TimerEngine(loaded)
        ringCurrentStage(engine)
        engine.dismissAlarm()
        assertEquals(1, engine.currentIndex)
        assertEquals(120, engine.timeRemaining)
    }

    @Test
    fun calendarTemplateSaveLoadKeepsModeTypesLabelsAndSequence() {
        val template = TimerPreset(
            id = "custom_2",
            name = "Focus Relax",
            mode = "calendar",
            sequence = intArrayOf(1500, 300),
            sequenceTypes = arrayOf("focus", "relax"),
            sequenceLabels = arrayOf("Deep Focus", "Reset"),
            alarmBehavior = "alarm"
        ).normalized(logFailures = true)
        val loaded = roundTrip(template)

        assertEquals("calendar", loaded.mode)
        assertEquals(2, loaded.sequence.size)
        assertEquals(2, loaded.sequenceTypes.size)
        assertEquals(2, loaded.sequenceLabels.size)
        assertEquals("focus", loaded.sequenceTypes[0])
        assertEquals("relax", loaded.sequenceTypes[1])
        assertEquals("Deep Focus", loaded.sequenceLabels[0])
        assertEquals("Reset", loaded.sequenceLabels[1])
        assertTrue(loaded.validate(logFailures = true))
    }

    @Test
    fun allDefaultPresetsValidateAfterNormalization() {
        val presets = getDefaultPresets("en")
        var i = 0
        while (i < presets.size) {
            assertTrue(presets[i].normalized(logFailures = true).validate(logFailures = true))
            i++
        }
    }

    private fun preset(id: String): TimerPreset {
        return getDefaultPresets("en").first { it.id == id }.normalized(logFailures = true)
    }

    private fun ringCurrentStage(engine: TimerEngine) {
        engine.restoreState(
            timeRemaining = 1,
            midTimeRemaining = engine.midTimeRemaining,
            bigTimeRemaining = engine.bigTimeRemaining,
            currentIndex = engine.currentIndex
        )
        engine.start()
        engine.tick()
        assertTrue(engine.isRinging)
    }

    private fun roundTrip(preset: TimerPreset): TimerPreset {
        val json = Json.encodeToString(listOf(preset))
        return Json.decodeFromString<List<TimerPreset>>(json)[0].normalized(logFailures = true)
    }
}
