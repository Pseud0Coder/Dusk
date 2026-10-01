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
| `INWORLD_KEY` | Voices for the Inworld coaches (Kelsey, Jonah, Priya, Dennis). Basic auth key from the Inworld portal. `INWORLD_MODEL` optional (default `inworld-tts-2`). |
| `FISH_KEY` | Voices for the Fish Audio coaches (Sarah, Adrian, Nova). `FISH_MODEL` optional (default `s2.1-pro`). |

Coaches: pick a persona in setup or Settings. Each has a personality the coach takes on and a voice from Inworld or Fish Audio. Without that provider's key, the persona still works with the phone's built-in voice.

## Voice mode
During setup you can choose "Talk it through" instead of tapping through questions. Dusk speaks, listens, and builds the same plan from the conversation. Voice is also available from the Coach tab and under the craving button.

## First run
1. Allow notifications when asked. Tap "Send a test notification" to check.
2. Pick your flow, then tap "Start day 1 today" when you're ready.
3. Coach: answer its questions. When it proposes a routine, tap "Use this routine". Each item becomes a daily reminder with a Done button.

Reminders survive reboots. If your phone kills them, set Dusk's battery usage to Unrestricted (Settings > Apps > Dusk > Battery).
