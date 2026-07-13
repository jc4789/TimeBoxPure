package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CompiledInstrumentBankTest {
    @Test
    fun buildKeepsOnlyUniqueSelectedPatchReferences() {
        val first = Patches.ZunLead1
        val second = Patches.ZunBass1
        val source = TestLookup(intArrayOf(10, 11, 20), arrayOf(first, first, second))
        val builder = CompiledInstrumentBankBuilder(source, 3, 0)

        assertEquals(0, builder.internFm(10))
        assertEquals(0, builder.internFm(11))
        assertEquals(1, builder.internFm(20))
        val bank = builder.build()

        assertEquals(2, bank.fmPatchCount)
        assertEquals(0, bank.ssgPatchCount)
        assertSame(first, bank.fmPatch(0))
        assertSame(second, bank.fmPatch(1))
    }

    @Test
    fun localIdsRemainStableInFirstSelectionOrder() {
        val source = TestLookup(
            intArrayOf(70, 30, 50),
            arrayOf(Patches.ZunChime1, Patches.ZunPad1, Patches.ZunBell1)
        )
        val builder = CompiledInstrumentBankBuilder(source, 3, 0)

        assertEquals(0, builder.internFm(50))
        assertEquals(1, builder.internFm(70))
        assertEquals(0, builder.internFm(50))
        assertEquals(2, builder.internFm(30))
    }

    @Test
    fun fmAndSsgFamiliesUseAllocationFreeIndexedLookup() {
        val fmPatch = Patches.ZunLead1
        val ssgPatch = SsgPatch(fixedLevel = 9)
        val source = TestLookup(intArrayOf(4), arrayOf(fmPatch), intArrayOf(4), arrayOf(ssgPatch))
        val builder = CompiledInstrumentBankBuilder(source, 1, 1)

        assertEquals(0, builder.internFm(4))
        assertEquals(0, builder.internSsg(4))
        val bank = builder.build()

        assertSame(fmPatch, bank.fmPatch(0))
        assertSame(ssgPatch, bank.ssgPatch(0))
        assertNull(bank.fmPatch(1))
        assertNull(bank.ssgPatch(-1))
    }

    @Test
    fun equalNumericLocalIdsAreScopedByBank() {
        val firstBuilder = CompiledInstrumentBankBuilder(
            TestLookup(intArrayOf(1), arrayOf(Patches.ZunLead1)), 1, 0
        )
        val secondBuilder = CompiledInstrumentBankBuilder(
            TestLookup(intArrayOf(1), arrayOf(Patches.ZunBass1)), 1, 0
        )

        assertEquals(0, firstBuilder.internFm(1))
        assertEquals(0, secondBuilder.internFm(1))
        assertSame(Patches.ZunLead1, firstBuilder.build().fmPatch(0))
        assertSame(Patches.ZunBass1, secondBuilder.build().fmPatch(0))
    }

    @Test
    fun fallbackLookupPrefersSongLocalPatch() {
        val localPatch = Patches.ZunChime1
        val builtInPatch = Patches.ZunLead1
        val local = TestLookup(intArrayOf(7), arrayOf(localPatch))
        val builtIn = TestLookup(intArrayOf(7, 8), arrayOf(builtInPatch, Patches.ZunBass1))
        val source = FallbackSourceInstrumentLookup(local, builtIn)
        val localId = source.sourceIdForName("7")
        val builtInId = source.sourceIdForName("8")

        assertSame(localPatch, source.fmPatch(localId))
        assertSame(Patches.ZunBass1, source.fmPatch(builtInId))
        assertEquals(7, localId and Int.MAX_VALUE)
        assertEquals(8, builtInId)
    }

    private class TestLookup(
        private val fmIds: IntArray = IntArray(0),
        private val fmPatches: Array<FmPatch> = emptyArray(),
        private val ssgIds: IntArray = IntArray(0),
        private val ssgPatches: Array<SsgPatch> = emptyArray()
    ) : SourceInstrumentLookup {
        override fun sourceIdForName(name: String): Int {
            val sourceId = name.toIntOrNull() ?: return -1
            var index = 0
            while (index < fmIds.size) {
                if (fmIds[index] == sourceId) return sourceId
                index++
            }
            index = 0
            while (index < ssgIds.size) {
                if (ssgIds[index] == sourceId) return sourceId
                index++
            }
            return -1
        }

        override fun fmPatch(sourceId: Int): FmPatch? {
            var index = 0
            while (index < fmIds.size) {
                if (fmIds[index] == sourceId) return fmPatches[index]
                index++
            }
            return null
        }

        override fun ssgPatch(sourceId: Int): SsgPatch? {
            var index = 0
            while (index < ssgIds.size) {
                if (ssgIds[index] == sourceId) return ssgPatches[index]
                index++
            }
            return null
        }
    }
}
