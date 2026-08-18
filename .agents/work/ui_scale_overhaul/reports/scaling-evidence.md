# Scope
Visual comparison of the supplied reverted-baseline, failed-overhaul, and landscape screenshots.

# Confirmed
- All eight Android screenshots are 1080x2424 image files; four are before and four are after the reverted overhaul.
- The failed overhaul did not merely correct scale: Template cards were collapsed so substantially more rows appeared; Entropy task rows collapsed and left a large empty region; Settings rows compressed and top content collided with the system status area.
- `Landscape.png` is 2559x1439 and confirms that the landscape UI scale remained too large. The procedural timer artwork is outside this UI task and supplies no authorized edit target.
- The old guide's broad fixed-cell scene rewrites caused the portrait regressions and did not solve the landscape UI scale.

# Rejected
- Rewriting Template, Entropy, or Settings layout as part of the scale fix: the supplied after images show this creates regressions.
- Treating the landscape UI issue as a platform-wrapper problem: current shared `DisplayScalePolicy` and HUD orientation logic already contain the authorized UI decisions.
- Editing `ActiveTimerScene`, `timerRadius`, or procedural artwork: those systems are not UI and are outside scope.

# Unknown
- None that block rewriting the guide.

# Recommendation
Limit the plan to the existing `DisplayScalePolicy.deriveScale` body/constants and the existing shared HUD orientation decision; preserve every scene, artwork, renderer, API, and platform file.
