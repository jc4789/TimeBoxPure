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
pattern tables, command usage, normalized semantic traces, and the production
Bad Apple lane comparison. Extracted music is never written into the project.

The capability report is deliberately fail-closed. Every opcode/subcommand is
reported as one of:

- `EXACT`: all seven downstream evidence layers are connected.
- `PARTIAL`: some authoring/runtime evidence exists, but at least one layer is
  missing or approximate.
- `OBSERVED_ONLY`: the offline scanner can retain the command, but no audited
  authoring/runtime path is recorded.
- `UNSUPPORTED`: no usable evidence is recorded.

The seven required evidence layers are normalized observation, authored
MML/control syntax, `CompiledOpnaSong`, `CompiledOpnaTimeline`, runtime
dispatch/ownership, reset behavior, and independent verification. `EXACT` is
derived from those fields; it is never asserted by a separate whitelist.
The current R0 table intentionally promotes no command to `EXACT` while the
remaining repair phases are incomplete. Hardware-LFO commands therefore fail
closed instead of inheriting the old broad preservation claim.

Example for the supplied TH04 archives:

```powershell
python -B tools/pmd_corpus_audit.py --thdat "D:\Programes\ym2608-info\thtk-bin-12\thdat.exe" --archive "D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04plus\幻想郷ED.DAT" --archive "D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04plus\東方幻想.郷" --trace-song ST00.M86 --trace-song ST02.M86 --trace-song ST03.M86 --trace-song STAFF.M86 --bad-apple-audit --json-out build\reports\pmd-corpus\th04.json --csv-out build\reports\pmd-corpus\non-exact.csv --strict
```

Use repeated `--oracle-song NAME.M86` arguments for the decompiler view. Each
normalized oracle expands counted bytecode loops, decodes notes, controls, and
referenced FM patches, and records an authored part loop as `start_offset`,
`start_tick`, and `end_tick`. It stops after the first complete authored pass
instead of replaying the part loop until the trace step guard. The JSON remains
an offline semantic report under `build/reports`; it contains no extracted PMD
bytes.

The four `--trace-song` results above remain semantic part traces. They are not
the four independent named state/register checkpoint traces required by the
repair plan, and the report does not claim otherwise.

Independent checkpoint fixtures pass only when all four records have unique
names and unique song/part/source identities; archive and entry SHA-256 values;
format and driver profile; a nonempty register/state checkpoint payload; and an
explicit derivation record marked independent from the TimeBox runtime. Invalid
or duplicate records are excluded from the available count and reported as
schema errors. The checked-in table currently remains empty, so this gate stays
at `0/4` until real external evidence is supplied.

For a proposed catalog entry, request an explicit product assessment:

```powershell
python -B tools/pmd_corpus_audit.py <archive arguments> --product-candidate CANDIDATE.M86 --strict
```

Strict mode rejects the candidate if any used opcode/subcommand is not `EXACT`,
if the entry is missing or ambiguous, if scanning fails, or if the separate
four-trace independent state/register checkpoint gate is incomplete. Product
admission requires both per-capability verification and global trace readiness.
`.M86` and `.M26` entries are named explicitly; the tool does not silently
substitute one for the other.

Non-exact commands may be inspected only through an explicit non-catalog
research exception:

```powershell
python -B tools/pmd_corpus_audit.py <archive arguments> --research-fixture EXPERIMENT.M86 --strict
```

Research assessments always emit `catalog_eligible: false`. The same entry
cannot be both a product candidate and a research fixture.

JSON/CSV outputs are offline build reports. They are not runtime assets and
must not be copied into application resources.

`tools/oracles/logo_m86_normalized.json` is a compact semantic oracle generated
offline from the independently hashed `LOGO.M86` entry. It contains decoded
notes, controls, and the referenced 26-byte PMD FM voice as named register
fields, including the AM-enable bit separately from the five-bit DR value; it
contains no archive bytes and is never read by the application or
Gradle build. Schema 2 also binds the archive identifier/hash, entry hash,
format, and driver profile into the oracle hash. It is still a semantic oracle,
not an independent PMD register/state checkpoint trace. Regenerate it only from
the exact source and archive SHA-256 values recorded inside the fixture using
`--oracle-song LOGO.M86 --oracle-out <temporary-path>`, compare that temporary
result with the checked-in oracle, then run
`python -B -m unittest tools/pmd_corpus_audit_test.py`.
