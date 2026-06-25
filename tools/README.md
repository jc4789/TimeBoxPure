# Carmack-ZUN Audit Tools

These scripts are heuristic goblin traps for the Kotlin Multiplatform engine. They do not replace the compiler or human review.

Run from repository root:

```powershell
python tools/law_check.py .
python tools/hotpath_audit.py .
python tools/cinterop_audit.py .
```

Strict mode treats warnings as failures:

```powershell
python tools/law_check.py . --strict
```

JSON mode is useful for Codex summaries:

```powershell
python tools/cinterop_audit.py . --json
```

Suggested Codex workflow:

1. Read `AGENTS.md`.
2. Trigger the relevant skill.
3. Run the matching tool.
4. Fix `ERROR`.
5. Review or justify `WARN`.
