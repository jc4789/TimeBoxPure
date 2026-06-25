# Math Oracle Tools

These are Python standard-library scripts for making Python compute deterministic math and compact reports instead of asking the LLM to do arithmetic by vibes.

Run from repo root.

## LUTs

```powershell
python tools/math_oracles/gen_lut.py --kind sin --size 1024 --q 15 --object GeneratedSinLut --out GeneratedSinLut.kt
python tools/math_oracles/audio_lut_analyze.py --kind sin --size 1024 --amp 0.8
```

## Fixed point and affine scale

```powershell
python tools/math_oracles/fixed_point_oracle.py --frac-bits 16 --max-value 4096 --mul-a 4096 --mul-b 4096
python tools/math_oracles/affine_matrix_verify.py --cases
```

## IMGUI and glyph metrics

```powershell
python tools/math_oracles/imgui_layout_sim.py --logical-w 800 --u 16 --labels Start Settings "A very long label"
python tools/math_oracles/glyph_metric_oracle.py --text "開始" --u 16 --scale 2 --box-w 160 --box-h 48
```

## Vector / raster preview

```powershell
python tools/math_oracles/de_casteljau_oracle.py --points "0,0 32,64 64,0" --tolerance 0.5
python tools/math_oracles/vector_preview.py shape.json --dump
```

## Palette / framebuffer

```powershell
python tools/math_oracles/palette_verify.py Palette.kt
python tools/math_oracles/framebuffer_hash.py actual.bin --expected expected.bin
```

## Token saving

```powershell
python tools/math_oracles/repo_brief.py . --out ENGINE_BRIEF.generated.md
```

Rule: Python computes. Kotlin executes. The LLM reads compact reports.
