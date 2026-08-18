# Scope
Phase 0〜1 の DisplayGrid、共有境界、文字ラスタ利用箇所。
# Confirmed
- `DisplayScalePolicy` の直接利用は Android `surfaceChanged` と Win32 `applyClientSize`。
- `drawPhysicalRect` の利用は通常glyphとtext cursor矩形。
- 通常・polar・回転glyphは別経路だったため、共通source-grid emissionへ統合対象。
# Rejected
- プロシージャルアート固有のscale名はtext `cellSpan` rename対象外。
# Unknown
- 実機画面証拠。
# Recommendation
grid契約と文字ラスタを一括移行し、両platformコンパイルで境界を確認する。
