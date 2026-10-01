# Dusk roadmap

## Next major addition: Themes

Themes are complete design overhauls, not color overlays. Island sunset is the default.

A theme defines, end to end:
- **Palette and surfaces** for light and dark, including the background scene.
- **Typography**: font pairing, scale, and spacing.
- **Shape language**: corner radii, card style (magnets, glass, paper, flat), borders and shadows.
- **Iconography**: icon set and stroke weight.
- **Scene and gamification art**: what the sky, gulls, sunsets, and island become in that world (for example forest: fireflies instead of gulls, a growing tree instead of an island).
- **Motion**: animation style and pacing, always respecting reduce motion.
- **Voice and copy tone**: how the coach and notifications sound, plus a matching Kokoro voice.
- **Applies on the backend too**: the theme is passed to the coach so its language, metaphors, and generated plan match the theme.

Implementation sketch:
1. A `DuskTheme` data model that holds every token above, with the current island sunset as theme #1.
2. Move all hard-coded art and copy behind the theme (scene renderer, magnet styles, milestone names).
3. Theme picker in Settings with live previews, saved per person.
4. Coach prompt gets a theme block so generated plans use the theme's metaphors.
5. Two to three launch themes, each tested for contrast in light and dark.

## Later
- Small proxy server so the OpenRouter key never ships inside the APK.
- Streaming voice (start speaking after the first sentence).
