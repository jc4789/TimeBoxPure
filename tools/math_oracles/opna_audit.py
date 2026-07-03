#!/usr/bin/env python3
import os
import sys
import re

# Banned tokens to search for in OPNA audio hot-paths
BANNED_TOKENS = [
    r"Random", r"nextFloat", r"nextInt", r"nextDouble",
    r"mutableListOf", r"arrayListOf",
    r"\.map\b", r"\.flatMap\b", r"buildList", r"generateSequence",
    r"arrayOf", r"Pair\s*\(", r"\bList<", r"\bMap<", r"\bSet<", r"\bSequence<",
    r"copyOf", r"copyOfRange",
    r"kotlin\.math\.sin", r"\bsin\(",
    r"java\.", r"System\.currentTimeMillis", r"System\.nanoTime", r"Thread\b", r"Runtime\b"
]

def extract_functions(content):
    """
    Finds functions (render, renderOne, noteOn, noteOff, trigger*) and returns their bodies.
    Uses basic brace matching to find the extent of the function body.
    """
    func_pattern = re.compile(
        r"\bfun\s+(render[A-Za-z0-9_]*|noteOn[A-Za-z0-9_]*|noteOff[A-Za-z0-9_]*|"
        r"trigger[A-Za-z0-9_]*|computeOp[A-Za-z0-9_]*|advanceOp|clockEnvelope|"
        r"panMonoToStereo|prepare|handleSequencerEvent|setLfoFrame|clockPitchRamp|"
        r"clockSpecialPitchRamps|operatorAttenuation|advancePhase|leftPanGain|rightPanGain)\s*\("
    )
    bodies = []
    
    pos = 0
    while True:
        match = func_pattern.search(content, pos)
        if not match:
            break
        
        # Find the starting brace { of the function body
        start_idx = content.find("{", match.end())
        if start_idx == -1:
            pos = match.end()
            continue
            
        # Match braces to find the end of the body
        depth = 1
        i = start_idx + 1
        while i < len(content) and depth > 0:
            if content[i] == '{':
                depth += 1
            elif content[i] == '}':
                depth -= 1
            i += 1
            
        if depth == 0:
            body = content[start_idx:i]
            bodies.append((match.group(0), body))
            pos = i
        else:
            pos = start_idx + 1
            
    return bodies

def audit_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # We audit specific functions (hot-paths)
    funcs = extract_functions(content)
    violations = []
    
    for func_sig, body in funcs:
        for token_pattern in BANNED_TOKENS:
            matches = re.findall(token_pattern, body)
            if matches:
                violations.append((func_sig, token_pattern, matches))
                
    return violations

def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    opna_dir = os.path.join(root_dir, "shared-engine", "src", "commonMain", "kotlin", "com", "example", "timeboxvibe", "engine", "audio")
    
    files_to_audit = []
    
    # Find all Kotlin files under the audio/ folder
    for dirpath, _, filenames in os.walk(opna_dir):
        for filename in filenames:
            if filename.endswith(".kt"):
                files_to_audit.append(os.path.join(dirpath, filename))
                
    # Also add SoundPreviewPlayer.kt which is under the app module (though we mainly care about shared-engine commonMain, we check all of them)
    app_preview = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "timeboxvibe", "engine", "SoundPreviewPlayer.kt")
    if os.path.exists(app_preview):
         files_to_audit.append(app_preview)

    print("=========================================")
    print("OPNA AUDIO HOT-PATH AUDIT")
    print("=========================================")
    
    failed = False
    for filepath in files_to_audit:
        rel_path = os.path.relpath(filepath, root_dir)
        violations = audit_file(filepath)
        if violations:
            print(f"[FAIL] {rel_path}")
            for func_sig, pattern, matches in violations:
                print(f"  - In function '{func_sig.strip()}': found banned token pattern '{pattern}' (matched: {matches})")
            failed = True
        else:
            print(f"[PASS] {rel_path}")
            
    print("=========================================")
    if failed:
        print("AUDIT RESULT: FAILED")
        sys.exit(1)
    else:
        print("AUDIT RESULT: PASSED")
        sys.exit(0)

if __name__ == "__main__":
    main()
