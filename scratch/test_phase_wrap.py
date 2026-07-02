def simulate_phase_wrap():
    phase_step = 5357358
    phase = 0
    
    for i in range(1000):
        phase = (phase + phase_step)
        if phase > 0x7FFFFFFF:
            phase = phase - 0x100000000
            
        phase_addr = ((phase & 0xFFFFFFFF) >> 19) & 1023
        
        pure_phase = ((i + 1) * phase_step) % (1 << 29)
        expected_addr = (pure_phase >> 19) & 1023
        
        if phase_addr != expected_addr:
            print(f"MISMATCH at i={i}: phase={phase}, phase_addr={phase_addr}, expected={expected_addr}")
            return
            
    print("No mismatches found in 1000 samples.")

simulate_phase_wrap()
