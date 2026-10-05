# Dusk

A quit coach that doesn't hype recovery. After dusk comes the night: Dusk tells you the truth about what's coming and helps you prepare. See PERSONALITY.md. An AI coach (DeepSeek V4.1 Flash via OpenRouter) gets to know your day, maps a research-based withdrawal timeline onto your real calendar, and turns it into a daily routine with reminders.

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
| `FISH_KEY` | Voices for the Fish Audio coaches (Sarah, Adrian, Nova). `FISH_MODEL` optional (default `s2.1-pro-free`, the free fair-use tier; set `s2.1-pro` once the account has API credit). |

Coaches: pick a persona in setup or Settings. Each has a personality the coach takes on and a voice from Inworld or Fish Audio. Without that provider's key, the persona still works with the phone's built-in voice.

## Progress tracking
The Progress tab shows time clear (live, per substance), where you are on the withdrawal tide, time to the next milestone, every logged craving (passed vs gave in, pass rate, typical minutes to pass), the last 7 days, the hours cravings hit, likely cravings for the rest of today, and rough estimates of what you got back. Cravings are logged from the craving button, check-ins, and voice; the coach sees the summary.

## Home screen widgets
Six widgets, each in the widget picker: Day (1×1), Clear for (2×1, live timer), Craving (2×2, one-tap craving button), Status (4×1), Island (4×2, your sky, gulls and island), and Today (4×3, check off routine items from the home screen). They follow light and dark mode and refresh whenever something changes in the app.

## Check-ins (optional)
Dusk can send a short question before your hard moments, timed about 45 minutes ahead of the triggers in your routine. Each check-in offers: Check in (one tap), Talk (opens the coach), or Not now. Off unless chosen; pick 1 to 3 a day, for the first 2 weeks or ongoing, in setup or Settings. Check-ins have their own Android notification category.

## Voice mode
During setup you can choose "Talk it through" instead of tapping through questions. Dusk speaks, listens, and builds the same plan from the conversation. Voice is also available from the Coach tab and under the craving button.

## First run
1. Allow notifications when asked. Tap "Send a test notification" to check.
2. Pick your flow, then tap "Start day 1 today" when you're ready.
3. Coach: answer its questions. When it proposes a routine, tap "Use this routine". Each item becomes a daily reminder with a Done button.

Reminders survive reboots. If your phone kills them, set Dusk's battery usage to Unrestricted (Settings > Apps > Dusk > Battery).

## Publishing to Google Play
The public APK above is for sideloading and is signed with a debug key. Google Play needs a bundle (`.aab`) signed with your own **upload key**. CI builds it for you once the secrets below exist.

**1. Make the upload key (once, on your own computer).** Keep the file and its passwords somewhere safe. Never commit them. Play also keeps its own app-signing key, so a lost upload key can be reset, but it takes a support request.
```bash
keytool -genkeypair -v -keystore dusk-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 dusk-upload.jks        # macOS: base64 -i dusk-upload.jks
```

**2. Add four more GitHub secrets** (Settings > Secrets and variables > Actions):

| Secret | Value |
| --- | --- |
| `DUSK_KEYSTORE_B64` | the base64 text from the command above |
| `DUSK_KEYSTORE_PASSWORD` | the keystore password you typed |
| `DUSK_KEY_ALIAS` | `upload` |
| `DUSK_KEY_PASSWORD` | the key password you typed |

**3. Build the bundle.** Run the *Build APK* workflow (Actions > Build APK > Run workflow). Download **Dusk-aab** from the run's artifacts. Branch builds only produce artifacts. Only `main` updates the public "latest" release. Every build gets a new `versionCode` from the run number, which Play requires.

**4. Upload to an internal test track.** Play Console > Create app > Testing > Internal testing > Create new release > upload `Dusk.aab`. Accept Play App Signing when asked. Add testers by email, then open the opt-in link on their phones. Testers who sideloaded the APK must uninstall it first, because the two builds are signed with different keys.

**5. Fill in App content** before a public release: privacy policy URL, Data safety, content rating, target audience, the Health apps declaration, and the exact-alarm permission declaration.

The app targets Android 16 (API 36).
