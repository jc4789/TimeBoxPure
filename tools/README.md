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

## Offline PMD corpus audit

`pmd_corpus_audit.py` uses THTK to extract only requested `.M86`/`.M26` files
into an OS temporary directory. It inventories playable PMD parts, rhythm
pattern tables, command usage, normalized note/state traces, and the production
Bad Apple lane comparison. Extracted music is never written into the project.

Example for the supplied TH04 archives:

```powershell
python tools/pmd_corpus_audit.py --thdat "D:\Programes\ym2608-info\thtk-bin-12\thdat.exe" --archive "D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04plus\幻想郷ED.DAT" --archive "D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04plus\東方幻想.郷" --trace-song ST00.M86 --trace-song ST02.M86 --trace-song ST03.M86 --trace-song STAFF.M86 --bad-apple-audit --json-out build\reports\pmd-corpus\th04.json --csv-out build\reports\pmd-corpus\unsupported.csv --strict
```

JSON/CSV outputs are offline build reports. They are not runtime assets and
must not be copied into application resources.

`tools/oracles/logo_m86_normalized.json` is a compact semantic oracle generated
offline from the independently hashed `LOGO.M86` entry. It contains decoded
notes, controls, and the referenced 26-byte PMD FM voice as named register
fields; it contains no archive bytes and is never read by the application or
Gradle build. Regenerate it only from the exact source SHA-256 recorded inside
the fixture using `--oracle-song LOGO.M86 --oracle-out <temporary-path>`, compare
that temporary result with the checked-in oracle, then run
`python -m unittest tools/pmd_corpus_audit_test.py`.
