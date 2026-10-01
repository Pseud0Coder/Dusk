# Dusk

A minimal Android quit coach. An AI coach (DeepSeek V4.1 Flash via OpenRouter) gets to know your day, maps a research-based withdrawal timeline onto your real calendar, and turns it into a daily routine with reminders.

Two flows, each with its own coach, timeline, routine and day count. You pick one when you first open the app and can switch in Settings.

## Get the APK
1. Open the repo's **Actions** tab and the latest "Build APK" run.
2. Download **Dusk-apk**, unzip it, and install `app-debug.apk` (allow "install unknown apps" when asked).

## First run
1. Settings: paste your OpenRouter key (openrouter.ai/keys) and tap Save. The model defaults to `deepseek/deepseek-v4.1-flash`.
2. Allow notifications when asked. Tap "Send a test notification" to check.
3. Pick your flow, then tap "Start day 1 today" when you're ready.
4. Coach: answer its questions. When it proposes a routine, tap "Use this routine". Each item becomes a daily reminder with a Done button.

Reminders survive reboots. If your phone kills them, set Dusk's battery usage to Unrestricted (Settings > Apps > Dusk > Battery).
