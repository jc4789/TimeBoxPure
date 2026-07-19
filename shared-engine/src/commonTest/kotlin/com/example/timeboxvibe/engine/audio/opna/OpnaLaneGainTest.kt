package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaLaneGainTest {
    @Test
    fun laneGainsRemainInsideTheEstablishedMixRange() {
        assertTrue(OpnaAudioConstants.LANE_GAIN_LEAD in 0.3f..0.9f)
        assertTrue(OpnaAudioConstants.LANE_GAIN_HARMONY in 0.3f..0.9f)
        assertTrue(OpnaAudioConstants.LANE_GAIN_BASS in 0.3f..0.9f)
        assertTrue(OpnaAudioConstants.LANE_GAIN_PERCUSSION in 0.3f..0.9f)
    }

    @Test
    fun bassToLeadGainRatioRemainsAudible() {
        assertTrue(OpnaAudioConstants.LANE_GAIN_BASS / OpnaAudioConstants.LANE_GAIN_LEAD >= 0.4f)
    }
}
