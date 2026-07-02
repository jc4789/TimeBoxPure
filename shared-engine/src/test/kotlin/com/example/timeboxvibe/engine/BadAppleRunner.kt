package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.mml.MmlSongBank
import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() {
    val sampleRate = 48000
    val arrangement = MmlSongBank.getArrangement(MmlSongBank.SENBONZAKURA_DEMO_KEY, 1.0f)!!
    
    val synth = OpnaLikeSynthesizer(sampleRate)
    synth.enableOutputFilter = true
    for (fm in synth.fm) fm.enableOversampling = true
    
    val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
    MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
    
    val endMs = 10000 // Just render 10 seconds for testing
    val totalFrames = (endMs * sampleRate / 1000) + (sampleRate * 2) // 2 seconds tail
    
    val buffer = FloatArray(totalFrames)
    synth.render(buffer, buffer.size, sequencer, 0L)
    
    FileOutputStream("C:\\Users\\cesta\\.gemini\\antigravity-ide\\brain\\fca27a81-3847-40c0-8674-a8b083a30ff7\\scratch\\bad_apple.raw").use { out ->
        val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in buffer) {
            val shortVal = (sample.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
            byteBuffer.putShort(shortVal)
        }
        out.write(byteBuffer.array())
    }
}
