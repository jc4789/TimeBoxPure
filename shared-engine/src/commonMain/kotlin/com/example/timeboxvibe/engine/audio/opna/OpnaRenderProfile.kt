package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.AudioLaws

/** Immutable playback policy. Authored song data never carries caller output gain. */
class OpnaRenderProfile private constructor(
    val sampleRate: Int,
    val maximumRenderChunk: Int,
    val enableFmOversampling: Boolean,
    val outputProfile: OpnaOutputProfile,
    val chipMixHeadroom: Float,
    val outputGain: Float,
    val masterGain: Float,
    val enableOutputFilter: Boolean,
    val outputFilterAlpha: Float,
    val enableStereoResonator: Boolean,
    private val eqTypeCodes: IntArray,
    private val eqFrequenciesHz: FloatArray,
    private val eqGainsDb: FloatArray,
    private val eqQualities: FloatArray,
    val userGain: Float
) {
    val eqBandCount: Int get() = eqTypeCodes.size

    init {
        require(sampleRate > 0) { "OPNA render sample rate must be positive" }
        require(maximumRenderChunk in 1..OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK) {
            "OPNA render chunk exceeds the synthesizer primitive buffer"
        }
        require(userGain >= 0f) { "OPNA user gain must be non-negative" }
        require(
            eqTypeCodes.size == eqFrequenciesHz.size &&
                eqTypeCodes.size == eqGainsDb.size &&
                eqTypeCodes.size == eqQualities.size
        ) { "OPNA EQ primitive storage must have matching lengths" }
    }

    fun eqBandTypeCode(index: Int): Int = eqTypeCodes[index]
    fun eqBandFrequencyHz(index: Int): Float = eqFrequenciesHz[index]
    fun eqBandGainDb(index: Int): Float = eqGainsDb[index]
    fun eqBandQ(index: Int): Float = eqQualities[index]

    internal fun createPlayer(program: CompiledOpnaSong): CompiledOpnaPlayer =
        CompiledOpnaPlayer(CompiledOpnaTimelineFactory.build(program, sampleRate))

    companion object {
        const val PRODUCT_SAMPLE_RATE = 48_000
        const val EQ_TYPE_PEAK: Int = 0

        fun product(arrangement: ArrangementLanes, userGain: Float): OpnaRenderProfile =
            create(
                sampleRate = PRODUCT_SAMPLE_RATE,
                arrangement = arrangement,
                enableFmOversampling = true,
                outputProfile = OpnaOutputProfile.TIMEBOX_LEGACY,
                userGain = userGain,
                enableOutputFilter = true,
                outputFilterAlpha = 0.50f,
                enableStereoResonator = false
            )

        internal fun inspection(
            sampleRate: Int,
            arrangement: ArrangementLanes? = null,
            outputProfile: OpnaOutputProfile = OpnaOutputProfile.TIMEBOX_LEGACY,
            userGain: Float = 1f,
            enableOutputFilter: Boolean = true,
            outputFilterAlpha: Float = 0.50f,
            enableStereoResonator: Boolean = false,
            enableFmOversampling: Boolean = false
        ): OpnaRenderProfile = create(
            sampleRate = sampleRate,
            arrangement = arrangement,
            enableFmOversampling = enableFmOversampling,
            outputProfile = outputProfile,
            userGain = userGain,
            enableOutputFilter = enableOutputFilter,
            outputFilterAlpha = outputFilterAlpha,
            enableStereoResonator = enableStereoResonator
        )

        internal fun inspectionPlayer(
            arrangement: ArrangementLanes,
            sampleRate: Int
        ): CompiledOpnaPlayer = inspection(sampleRate).createPlayer(arrangement.compiledOpnaSong)

        private fun create(
            sampleRate: Int,
            arrangement: ArrangementLanes?,
            enableFmOversampling: Boolean,
            outputProfile: OpnaOutputProfile,
            userGain: Float,
            enableOutputFilter: Boolean,
            outputFilterAlpha: Float,
            enableStereoResonator: Boolean
        ): OpnaRenderProfile {
            val bandCount = arrangement?.eqBands?.size ?: 0
            val typeCodes = IntArray(bandCount)
            val frequenciesHz = FloatArray(bandCount)
            val gainsDb = FloatArray(bandCount)
            val qualities = FloatArray(bandCount)
            var bandIndex = 0
            while (bandIndex < bandCount) {
                val band = arrangement!!.eqBands[bandIndex]
                typeCodes[bandIndex] = band.type.ordinal
                frequenciesHz[bandIndex] = band.frequencyHz
                gainsDb[bandIndex] = band.gainDb
                qualities[bandIndex] = band.q
                bandIndex++
            }
            return OpnaRenderProfile(
                sampleRate = sampleRate,
                maximumRenderChunk = OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK,
                enableFmOversampling = enableFmOversampling,
                outputProfile = outputProfile,
                chipMixHeadroom = AudioLaws.CHIP_MIX_HEADROOM,
                outputGain = AudioLaws.OPNA_OUTPUT_GAIN,
                masterGain = OpnaAudioConstants.MASTER_GAIN,
                enableOutputFilter = enableOutputFilter,
                outputFilterAlpha = outputFilterAlpha,
                enableStereoResonator = enableStereoResonator,
                eqTypeCodes = typeCodes,
                eqFrequenciesHz = frequenciesHz,
                eqGainsDb = gainsDb,
                eqQualities = qualities,
                userGain = userGain
            )
        }
    }
}
