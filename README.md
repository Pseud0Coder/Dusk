# Dusk

A minimal Android quit coach. An AI coach (DeepSeek V4.1 Flash via OpenRouter) gets to know your day, maps a research-based withdrawal timeline onto your real calendar, and turns it into a daily routine with reminders.

Two flows, each with its own coach, timeline, routine and day count. You pick one when you first open the app and can switch in Settings.

## Get the APK
Download the latest build: https://github.com/Pseud0Coder/Dusk/releases/latest/download/Dusk.apk

Open it on your phone and allow "install unknown apps" when asked.

## Keys (GitHub secrets, never in the code)
Add these in the repo under Settings > Secrets and variables > Actions > New repository secret. The next build picks them up.

| Secret | What it does |
| --- | --- |
| `OPENROUTER_KEY` | Powers the coach. With it set, people never see a key screen. |
| `INWORLD_KEY` | Optional. Natural voice via Inworld TTS (Basic auth key). `INWORLD_VOICE` and `INWORLD_MODEL` are optional overrides (defaults: `Sarah`, `inworld-tts-2`). |
| `FISH_KEY` | Optional. Natural voice via Fish Audio. `FISH_VOICE_ID` picks a voice from their library; `FISH_MODEL` defaults to `s2.1-pro`. |

With no voice key, voice mode uses the phone's free on-device speech. Inworld is used if both voice keys are set.

## Voice mode
During setup you can choose "Talk it through" instead of tapping through questions. Dusk speaks, listens, and builds the same plan from the conversation. Voice is also available from the Coach tab and under the craving button.

## First run
1. Allow notifications when asked. Tap "Send a test notification" to check.
2. Pick your flow, then tap "Start day 1 today" when you're ready.
3. Coach: answer its questions. When it proposes a routine, tap "Use this routine". Each item becomes a daily reminder with a Done button.

Reminders survive reboots. If your phone kills them, set Dusk's battery usage to Unrestricted (Settings > Apps > Dusk > Battery).
