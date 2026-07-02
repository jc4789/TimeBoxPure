package com.example.timeboxvibe.engine.audio.opna

enum class EgMode {
    /** Converts the source-compatible float ADSR fields to legal OPN rates before rendering. */
    LEGACY_ADSR,
    /** Uses the explicit AR/DR/SR/SL/RR/KS register fields. */
    OPN_RATE
}
