# Scope
Android/Win32 resize、render、clear、input、wheel、DPI経路。
# Confirmed
- Androidはsurface寸法、Win32は`GetClientRect`が物理寸法入口。
- Win32 framebuffer pinningは同期`StretchDIBits`内で完結し、所有権変更不要。
- wheelは物理delta生成後に通常入力逆変換へ入る。
# Rejected
- 旧separate scale/logical fields、DPI layout入力、remainder入力許可。
# Unknown
- 実行中の端末寸法と画面証拠。
# Recommendation
単一`DisplayGrid` snapshot、palette-black full clear、remainder拒否へ移行する。
