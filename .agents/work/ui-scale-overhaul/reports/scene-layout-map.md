# Scope
HUDと`Scenes.kt`全シーンのmacro UI候補。
# Confirmed
- HUD render/hitboxは共有helperから導出可能。
- Template、Settings、Entropyはrender/hitbox/scrollで式が複製されており同時更新が必要。
- ActiveTimerのprocedural art比率はUI macro対象外。
# Rejected
- `Pc98GraphicsHardware.kt`はpalette状態のみでlayout変更不要。
- micro bevel/cursor/internal barの`U/8`は許可。
# Unknown
- サブシーンの未指定割合はnamed ratioをquarter-gridへsnapすることで一般法へ適合させる必要がある。
# Recommendation
明示式を先に適用し、サブシーンは最新ユーザー指示に従い同じcanonical UI法へ移行する。
