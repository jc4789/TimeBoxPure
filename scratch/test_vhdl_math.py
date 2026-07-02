import math

# VHDL simulation of YM2608 phase modulation

# Phase accumulator: 32-bit unsigned
# Sine index: bits 28..19 of theta

def simulate_vhdl():
    # Maximum operator output in VHDL
    # sine_s (max 255) * env_s (max 65025) = 16,581,375
    # output = sample(25 downto 8) = 16,581,375 >> 8 = 64770
    max_output = 64770
    print(f"Max VHDL operator output: {max_output}")

    # Modulation math:
    # in1 = signed(input(16 downto 0) & "000000000000000") = input << 15
    in1 = max_output << 15
    print(f"in1 (modulation word): {in1}")

    # The sine index takes bits 28..19 of (phase + in1)
    # This means the full cycle of the sine index is 2^29
    phase_cycle = 1 << 29
    print(f"VHDL phase cycle: {phase_cycle}")

    # Calculate how many cycles of modulation this is
    modulation_cycles = in1 / phase_cycle
    print(f"Modulation Index (cycles): {modulation_cycles:.4f}")
    
    # Prove that Kotlin ushr 22 is 8x weaker
    kotlin_old_phase_cycle = 1 << 32
    kotlin_old_modulation_cycles = in1 / kotlin_old_phase_cycle
    print(f"Kotlin old (ushr 22) Modulation Index (cycles): {kotlin_old_modulation_cycles:.4f}")
    print(f"Ratio (VHDL / Kotlin): {modulation_cycles / kotlin_old_modulation_cycles:.1f}x stronger")

if __name__ == "__main__":
    simulate_vhdl()
