package com.example.timeboxvibe.engine.audio.opna

/** Setup-time lookup for authored or song-local source instrument identifiers. */
internal interface SourceInstrumentLookup {
    fun sourceIdForName(name: String): Int
    fun fmPatch(sourceId: Int): FmPatch?
    fun ssgPatch(sourceId: Int): SsgPatch?
}

/** Resolves song-local instruments first and shared built-ins second. */
internal class FallbackSourceInstrumentLookup(
    private val local: SourceInstrumentLookup,
    private val builtIn: SourceInstrumentLookup
) : SourceInstrumentLookup {
    override fun sourceIdForName(name: String): Int {
        val localId = local.sourceIdForName(name)
        if (localId >= 0) return localId or LOCAL_SOURCE_MASK
        return builtIn.sourceIdForName(name)
    }

    override fun fmPatch(sourceId: Int): FmPatch? = when {
        sourceId == MISSING_SOURCE_ID -> null
        sourceId < 0 -> local.fmPatch(sourceId and LOCAL_SOURCE_ID_MASK)
        else -> builtIn.fmPatch(sourceId)
    }

    override fun ssgPatch(sourceId: Int): SsgPatch? = when {
        sourceId == MISSING_SOURCE_ID -> null
        sourceId < 0 -> local.ssgPatch(sourceId and LOCAL_SOURCE_ID_MASK)
        else -> builtIn.ssgPatch(sourceId)
    }

    private companion object {
        const val LOCAL_SOURCE_MASK: Int = Int.MIN_VALUE
        const val LOCAL_SOURCE_ID_MASK: Int = Int.MAX_VALUE
        const val MISSING_SOURCE_ID = -1
    }
}

/** Exact compact patch references addressed by bank-local, family-specific IDs. */
internal class CompiledInstrumentBank internal constructor(
    private val fmPatches: Array<FmPatch?>,
    private val ssgPatches: Array<SsgPatch?>
) {
    val fmPatchCount: Int get() = fmPatches.size
    val ssgPatchCount: Int get() = ssgPatches.size

    fun fmPatch(localId: Int): FmPatch? = if (localId in fmPatches.indices) fmPatches[localId] else null

    fun ssgPatch(localId: Int): SsgPatch? = if (localId in ssgPatches.indices) ssgPatches[localId] else null
}

/** Setup-only reference interner; [build] trims both families to exact arrays. */
internal class CompiledInstrumentBankBuilder(
    private val source: SourceInstrumentLookup,
    maxFmPatches: Int,
    maxSsgPatches: Int
) {
    private val fmPatches: Array<FmPatch?>
    private val ssgPatches: Array<SsgPatch?>
    private var fmCount = 0
    private var ssgCount = 0

    init {
        require(maxFmPatches >= 0) { "FM instrument capacity must not be negative" }
        require(maxSsgPatches >= 0) { "SSG instrument capacity must not be negative" }
        fmPatches = arrayOfNulls(maxFmPatches)
        ssgPatches = arrayOfNulls(maxSsgPatches)
    }

    fun internFm(sourceId: Int): Int {
        val patch = source.fmPatch(sourceId) ?: return MISSING_LOCAL_ID
        var localId = 0
        while (localId < fmCount) {
            if (fmPatches[localId] === patch) return localId
            localId++
        }
        check(fmCount < fmPatches.size) { "FM instrument capacity exceeded" }
        fmPatches[fmCount] = patch
        return fmCount++
    }

    fun internSsg(sourceId: Int): Int {
        val patch = source.ssgPatch(sourceId) ?: return MISSING_LOCAL_ID
        var localId = 0
        while (localId < ssgCount) {
            if (ssgPatches[localId] === patch) return localId
            localId++
        }
        check(ssgCount < ssgPatches.size) { "SSG instrument capacity exceeded" }
        ssgPatches[ssgCount] = patch
        return ssgCount++
    }

    fun build(): CompiledInstrumentBank {
        val exactFm: Array<FmPatch?> = arrayOfNulls(fmCount)
        var index = 0
        while (index < fmCount) {
            exactFm[index] = fmPatches[index]
            index++
        }
        val exactSsg: Array<SsgPatch?> = arrayOfNulls(ssgCount)
        index = 0
        while (index < ssgCount) {
            exactSsg[index] = ssgPatches[index]
            index++
        }
        return CompiledInstrumentBank(exactFm, exactSsg)
    }

    companion object {
        const val MISSING_LOCAL_ID = -1
    }
}
