**Vega · Build Guide**

15-day progress0%

1. [Overview & scope](#overview)
2. [Target hardware](#hardware)
3. [Architecture](#architecture)
4. [Technology stack](#stack)
5. [AI models](#models)
6. [Project structure](#structure)
7. [Core design rules](#core)
8. [UI theme](#theme)
9. [Day 0 setup](#setup)
10. [15-day plan](#plan)
11. [Daily testing](#testing)
12. [Day 15 demo](#demo)
13. [Risks & plan B](#risks)
14. [After day 15](#later)
15. [Reference documents](#refs)

Implementation reference · v1.1

# Vega Implementation Guide

An offline voice assistant for an Android car head unit. It wakes on Hey Vega, understands speech, decides with a small on-device language model, acts on the head unit, and replies by voice. This guide covers the first 15 days: from an empty project to a working in-car demo.

*Lead* Niharika *Start* 24 Oct 2026 *Platform* Android 14 only *Goal* Working demo in 15 days

State **Listening**

“Play Hanuman Chalisa on YouTube”

### Scope for the 15-day build

#### In the demo

- Wake word, voice detection, live speech-to-text, spoken replies
- Qwen language model with a quick fine-tune
- Open and close apps, volume, settings pages
- YouTube and Spotify search, pick a result, pause / next / previous
- Navigation: places, home, office, nearby search
- Calls, incoming call announcements
- SMS draft → review → edit → send
- Message announcements with privacy ON / OFF / ASK, WhatsApp basics
- Welcome and goodbye, male / female voice setting
- Simulated car panel for AC and windows

#### After day 15

- Real window, AC and seat control (needs the CAN-box SDK)
- Custom-trained wake word
- Speech-to-text fine-tuned for Indian English
- Hinglish, Hindi, Marathi
- Car-manual question answering
- Backend: weather, web search, bigger model
- Cab booking, driver voice ID, over-the-air updates

Rule

Build one thin slice end to end first, then improve each part. Every day ends with something running on the head unit.

Target

## Target hardware

An aftermarket Android head unit. These specs shape every technical choice in this guide.

| Item | Value | What it means for us |
| --- | --- | --- |
| Chip | Qualcomm SM6225 | Mid-range, platform name `bengal` |
| CPU | 4× Cortex-A73 2.8 GHz + 4× Cortex-A53 1.9 GHz | Older cores without fast AI instructions. The LLM runs on the 4 big cores; always-on audio runs on a little core. |
| GPU | Adreno 610 | Weak; plan for CPU-only AI |
| Memory | 8 GB RAM / 256 GB storage | Plenty: the whole AI stack needs about 1.3 GB |
| OS | Android 14, tablet build | No standard car API; car signals come through the vendor's controller board |
| Screen | 2000 × 1200 landscape | Orb sits bottom-right, nearest the driver in Indian cars |
| Mic | Built-in pinhole on the bezel | Poor in a moving car; use an external mic |
| Connectivity | LTE, Wi-Fi, Bluetooth | Calls and SMS through the unit's SIM or the paired phone |

How it works

## Architecture

One Android app with a background service. Audio flows left to right; the brain decides, actions execute, and Vega answers by voice and on screen.

Hear**Microphone**16 kHz mono, always on

Wake**“Hey Vega”**Wake word or steering button

Listen**Voice detection**Finds the end of speech

Transcribe**Speech-to-text**Live captions, contact names boosted

Decide**Brain**Simple rules first, then Qwen LLM

Act**Actions**Apps, media, Maps, calls, SMS, car panel

Reply**Voice + orb**Text-to-speech, cards, toasts

Only weather and online searches need the internet. Everything else runs on the head unit.

### Assistant states

| State | What is happening | Next |
| --- | --- | --- |
| Idle | Waiting for the wake word or button | Listening |
| Listening | Recording and transcribing speech | Thinking (end of speech) |
| Thinking | Rules or LLM choose an action | Acting, Asking, or Confirming |
| Confirming | A message draft waits for “send”, “edit” or “cancel” | Acting or Idle |
| Choosing | Results are on screen; waiting for “number two” or a tap | Acting |
| Speaking | Vega replies; the user can interrupt | Follow-up (6 s), then Idle |
| In call | Only call commands are active | Idle |

What we use

## Technology stack

Rows tagged 15-day are needed now. Rows tagged Later come after the demo.

### Development tools

| Tool | Purpose | When |
| --- | --- | --- |
| Android Studio (latest stable) | Write, build and debug the app | 15-day |
| Android SDK 34, NDK r26+, CMake | Build for Android 14, including the C++ parts | 15-day |
| JDK 17 | Needed by Gradle | 15-day |
| adb + scrcpy | Install on the head unit, read logs, mirror its screen | 15-day |
| Git + GitHub | Share code between Niharika and Claude | 15-day |
| Python 3.11 | Training data, fine-tuning, test scripts | 15-day |
| Audacity | Listen to recorded test audio | 15-day |
| Android Studio Profiler / Perfetto | Find slow code, CPU and memory use | Later |

### Languages

| Language | Used for | When |
| --- | --- | --- |
| Kotlin | The whole Android app | 15-day |
| C / C++ via JNI | Running the models (mostly prebuilt libraries) | 15-day |
| Python | Data generation, fine-tuning, evaluation | 15-day |
| GBNF grammar | Forces the LLM output into Vega's command format | 15-day |

### Android building blocks

| Area | Technology | When |
| --- | --- | --- |
| Async code and state | Kotlin Coroutines + Flow | 15-day |
| Always-on assistant | Foreground Service (microphone type) + boot receiver | 15-day |
| Orb over other apps | WindowManager overlay + custom drawn View | 15-day |
| Cards and settings screens | Jetpack Compose | 15-day |
| Microphone | AudioRecord, 16 kHz mono | 15-day |
| Speaker, lowering music | AudioTrack, AudioManager audio focus | 15-day |
| Music and video control | MediaSessionManager + MediaController | 15-day |
| Reading notifications, WhatsApp reply | NotificationListenerService + RemoteInput | 15-day |
| Picking YouTube results, WhatsApp send | AccessibilityService | 15-day |
| Calls | TelecomManager, TelephonyCallback, ContactsContract | 15-day |
| SMS | SmsManager | 15-day |
| Opening apps | PackageManager + Intents | 15-day |
| Maps, YouTube, Spotify, WhatsApp | Deep links: `google.navigation:`, YouTube search, `spotify:search:`, `wa.me` | 15-day |
| Location | LocationManager (GPS) | 15-day |
| Settings storage | DataStore | 15-day |
| Logs, saved places | Room (SQLite) | 15-day |
| Ignition, steering-wheel key | Vendor broadcasts (found on day 1) | 15-day |
| Real car control | Vendor controller-board / CAN-box SDK | Later |

### AI libraries on the device

| Library | Purpose | License | When |
| --- | --- | --- | --- |
| sherpa-onnx (Android build) | Wake word, voice detection, speech-to-text, text-to-speech in one library | Apache-2.0 | 15-day |
| llama.cpp (Android example) | Runs the Qwen language model | MIT | 15-day |
| WebRTC Audio Processing | Echo cancellation and noise reduction | BSD | Later |
| MNN-LLM | Alternative LLM engine, only if faster | Apache-2.0 | Later |

### Training stack (used once, on a rented GPU)

| Tool | Purpose | When |
| --- | --- | --- |
| Hugging Face transformers + datasets | Load the model and data | 15-day |
| TRL + PEFT (LoRA) or Unsloth | Fine-tune Qwen | 15-day |
| llama.cpp converter + quantizer | Shrink the trained model into the file the head unit runs | 15-day |
| Cloud GPU (1× 24 GB, a few hours) | Training | 15-day |
| A large LLM API (one-off) | Generate 5–10k training examples | 15-day |
| openWakeWord tools, icefall / k2 / lhotse | Wake-word and speech-to-text training | Later |

### Hardware and accounts

| Item | Why | When |
| --- | --- | --- |
| Head unit + USB cable / Wi-Fi adb | Target device | 15-day |
| External mic (unit's mic jack) | The built-in mic is poor | 15-day |
| Test phone with SIM + WhatsApp | Send test calls and messages to the unit | 15-day |
| SIM in the unit, or a paired Bluetooth phone | Calls and SMS | 15-day |
| Google account, Spotify login, WhatsApp linked on the unit | Media and messaging | 15-day |
| GitHub, cloud GPU, LLM API accounts | Development | 15-day |
| 12 V bench supply with an ignition switch | Test greeting and goodbye on the desk | Optional |
| Bluetooth OBD-II dongle | Fuel level and engine fault codes | Later |

Brains and voice

## AI models

All pretrained except the language model, which gets one quick fine-tune between day 8 and day 11.

| Job | 15-day model | Approx. RAM | Upgrade later |
| --- | --- | --- | --- |
| Wake word | sherpa-onnx keyword spotter (small English Zipformer), keyword “hey vega”, no training | \~10 MB | Custom openWakeWord model |
| Voice detection | Silero VAD (`silero_vad.onnx`) | \~2 MB | Same |
| Speech-to-text | sherpa-onnx streaming Zipformer English, int8, with contact names as hotwords | 80–150 MB | Fine-tuned for Indian English |
| Language model | Qwen3.5-0.8B, GGUF Q4_K_M (fallback Qwen3-0.6B) | 600–750 MB | Full fine-tune, \~65k examples |
| Text-to-speech | Piper voices (fast), one male and one female | 60–120 MB | Kokoro (more voices, Hindi) |

Check

RAM figures are estimates. Day 1 benchmarks on the real head unit replace them.

Code layout

## Project structure

One Android app with clear packages, plus a Python folder for training.

Repository

```
vega/
├── app/src/main/java/com/vega/
│   ├── service/    VegaService, boot + ignition receivers
│   ├── audio/      mic capture, playback, audio focus
│   ├── speech/     wake word, voice detection, speech-to-text, text-to-speech (sherpa-onnx)
│   ├── llm/        llama.cpp connector, prompt, grammar, output parser, simple-command rules
│   ├── brain/      dialog logic, context, contact/app name matching, reply templates
│   ├── actions/    apps, media, youtube, spotify, nav, call, sms, whatsapp, settings, carsim
│   ├── ui/         orb overlay, captions, cards, toasts, settings (Compose)
│   └── data/       DataStore settings, Room logs and places
├── app/src/main/cpp/   JNI glue for llama.cpp
├── models/manifest.json   model files, versions, checksums (models are not in git)
├── training/
│   ├── spec/       tools.yaml, dsl.gbnf, style.md
│   ├── data_gen/   seeds, generators, validators
│   ├── finetune/   training configs and scripts
│   └── eval/       test set and scoring
└── docs/           this guide and the planning documents
```

Decisions that keep it fast

## Core design rules

### 1. Simple rules before the LLM

About 60 fixed phrases (“pause”, “next song”, “volume up”, “send it”, “number two”) are matched by rules and never reach the LLM. They respond in under 100 ms. Everything else goes to Qwen.

### 2. A short, strict command format

The LLM answers in one compact line per action. A grammar makes invalid output impossible, and short output keeps it fast on this CPU.

Vega command format

```
user:  [ctx t=18:40 spd=35 drv=Niharika priv=ask net=1 media=none draft=none]
       play hanuman chalisa on youtube
vega:  <t>media.search app=youtube query="hanuman chalisa"</t>

user:  open my window halfway and switch off the AC
vega:  <t>win.set pos=driver pct=50</t><t>hvac.power on=false</t>

user:  how are you?
vega:  <say>I'm great, Niharika! Where are we heading?</say>

line := <t>tool key=value…</t> | <say>text</say> | <ask>question</ask> | <esc/>
```

### 3. Tools for the 15-day build

| Area | Tools |
| --- | --- |
| Apps & system | `app.open` `app.close` `nav.ui` `volume.set` `set.page` `set.brightness` |
| Media | `media.search` `media.select` `media.ctrl` `media.info` |
| Navigation | `nav.to` `nav.saved` `nav.nearby` `nav.select` `nav.stop` |
| Calls | `call.place` `call.dial` `call.answer` `call.reject` `call.end` |
| Messages | `msg.draft` `msg.edit` `msg.send` `msg.cancel` `msg.read` `privacy.set` |
| Car (simulated) | `win.set` `win.adjust` `hvac.power` `hvac.temp` `hvac.fan` `light.ambient` |
| Other | `weather.get` `help` |

### 4. Names are resolved in code, not by the model

The model outputs `to="gargi"`. Kotlin matches it against contacts and aliases (Mom, Dad), with spelling tolerance for Indian names. If two contacts match, Vega asks which one.

### 5. Spoken confirmations come from templates

`win.set` becomes “Opening the driver window to 50%”. Templates are instant, consistent and easy to translate later.

### 6. Latency budget

| Stage | Target |
| --- | --- |
| End-of-speech wait | 600 ms |
| Speech-to-text final text | ≤ 200 ms |
| Simple-command rules / LLM | ≤ 100 ms / ≤ 1.3 s |
| Action + first spoken audio | ≤ 300 ms |
| **Total, end of speech to action** | **about 1.5 s (demo limit 2 s)** |

### 7. Safety rules from day one

- No message is ever sent without an explicit “send” or “yes”.
- YouTube video is audio-only while the car is moving.
- OTP and bank messages are never read aloud.
- Every action is logged with a trace ID.

How Vega looks

## UI theme

Vega's in-car look: a small orb at the screen edge, a caption beside it, and cards only when a decision is needed. Dark by default for night driving, with a day variant. These are the exact values to put into the Android theme.

### Screen preview

↱

**350 m**Turn right onto FC Road

W

**WhatsApp · Mom**Message from Mom

Private

SMS**Message to Rohan**

I'm running late, stuck in traffic. I'll be there by 7.

SendEditCancel

Say “send it”, “add …” or “cancel”

You“…and add that I'll be there by 7”

Scaled preview of the 2000 × 1200 screen. Google Maps keeps running underneath; Vega uses the bottom-right corner, nearest the driver in right-hand-drive cars. The newly added words in the draft are highlighted for 2 seconds.

### Design principles

#### For the driver

- One place to glance: the orb, always bottom-right
- Each glance under 2 s, each task under 12 s of total eyes-off-road time
- At most 3 options on screen while moving
- Never cover the Maps turn instruction or the vendor dock

#### For the look

- Dark first; day theme follows the headlight signal or the time
- Colour never works alone: every state also changes shape or icon
- Flat surfaces with a hairline border, no heavy shadows or blur
- Calm motion only, 60 fps on the Adreno 610

### Orb states

**Idle**56 dp dim dot, or hidden

**Listening**Cyan ring pulses with your voice

**Thinking**Violet-to-blue rotation

**Speaking**Teal core moves with Vega's voice

*?*

**Waiting for you**Confirm or choose; mic is open

*✓*

**Done**Green flash for 300 ms

*!*

**Didn't catch that**Amber with a small shake

*×*

**Mic muted**Red, stays small

Small badges on the orb's edge show Offline and Privacy ON. Tap the orb to talk, tap again to stop, long-press for quick settings.

### Colour tokens

Night theme (default)

**Listening**`#22D3EE`Orb, primary buttons, highlights

**Thinking**`#8B5CF6 → #3B82F6`Orb rotation only

**Speaking**`#14B8A6`Orb while Vega talks

**Success**`#22C55E`Sent, done, answer call

**Warning**`#F59E0B`Didn't hear, low fuel

**Danger**`#EF4444`Reject call, mic muted

**Surface**`#111827 · 92%`Cards, captions, banners

**Text**`#F9FAFB`Main text on surfaces

**Text secondary**`#9CA3AF`Hints, labels, timestamps

Day theme

**Listening**`#0891B2`Darker for contrast in sunlight

**Thinking**`#7C3AED → #2563EB`Orb rotation

**Speaking**`#0D9488`Orb while Vega talks

**Success**`#16A34A`Sent, done

**Warning**`#D97706`Didn't hear, low fuel

**Danger**`#DC2626`Reject, muted

**Surface**`#F8FAFC · 94%`Cards, captions, banners

**Text**`#0F172A`Main text

**Text secondary**`#475569`Hints, labels

Contrast

All text must reach at least 4.5 : 1 against its surface. Test both themes in direct sunlight on the real screen before freezing the values.

### Typography

One family: **Noto Sans**, with **Noto Sans Devanagari** for Hindi and Marathi so mixed text looks consistent. Sizes are large because the screen is read at arm's length in a moving car.

Greeting · 40 sp · SemiBold

Good morning, Niharika!

Card body · 32 sp · Regular

I'm running late, stuck in traffic.

Caption · 30 sp · Medium

“Play Hanuman Chalisa on YouTube”

Card title · 28 sp · SemiBold

Message to Rohan

Button · 28 sp · SemiBold

Send · Edit · Cancel

Toast · 24 sp · Medium

AC on · 22°

Hint · 22 sp · Regular

Say “send it” or “cancel”

Hindi · 32 sp

मम्मी का संदेश: घर आते समय सब्ज़ियाँ ले आना

Samples are shown at about two-thirds of real size.

### Shapes, sizes and spacing

| Element | Value |
| --- | --- |
| Orb | 56 dp idle · 120 dp active · 300 dp Companion greeting (parked only) |
| Orb position | Bottom-right, 24 dp from the edges (mirror setting for left-hand-drive cars) |
| Action card | Max 760 px wide, 28 dp corners, 24 dp padding, above the orb, right-aligned |
| Buttons | Pill shape, at least 76 dp high and 160 dp wide, 16 dp apart, max 3 per card |
| Caption strip | Pill shape, left of the orb, 2 lines max, hides 3 s after Vega finishes |
| Toast | Pill near the orb, icon + up to 5 words, hides after 2.5 s |
| Notification banner | Top-right, max 140 dp high, never over the Maps turn instruction |
| Spacing scale | 4 · 8 · 16 · 24 · 32 · 48 dp |
| Borders | 1 dp hairline at 8% white (night) or 8% ink (day); no drop shadows |

### Components

| Component | Used for | Behaviour |
| --- | --- | --- |
| Orb | State: listening, thinking, speaking | Always present; drawn with a custom View and Canvas |
| Caption strip | What you said and what Vega says | Grey while partial, white when final; hides message text when Privacy is ON |
| Action card | Message draft, choose a contact, nearby places, incoming call, step-by-step guide, risky-action confirm | One template: title row, body, up to 3 buttons, voice hint. Auto-dismiss after 20–30 s. |
| Result badges | YouTube and Spotify results | Numbered badges ① ② ③ drawn over the app's own results |
| Toast | Completed actions | “Driver window 50%”, “Sent to Rohan” |
| Banner | Incoming calls and messages | Slides in from the top-right; OTP and bank messages show as “OTP received” |
| “Video when parked” panel | YouTube while moving | Covers the video area; audio keeps playing |
| Companion face | Welcome and goodbye | Optional style, parked only; shrinks into the orb |

### Motion and sound

| Moment | Motion | Sound |
| --- | --- | --- |
| Wake | Orb grows 56 → 120 dp, 250 ms ease-out | Soft “ding” |
| Listening | Ring scale follows the mic level | — |
| Thinking | Gradient rotates, 2.2 s per turn | — |
| Speaking | Core scale follows Vega's voice level | — |
| Card in / out | Slide up 16 dp + fade, 220 ms | — |
| Draft edit | New words highlighted in cyan for 2 s | — |
| Error | Amber shake, 300 ms | Low “bonk” |
| Reduce-motion setting | Colour and icon changes only, no animation | Unchanged |

### Theme code for Jetpack Compose

ui/theme/VegaTheme.kt

```
package com.vega.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vega.R

data class VegaPalette(
    val listening: Color, val thinkingStart: Color, val thinkingEnd: Color,
    val speaking: Color, val success: Color, val warning: Color, val danger: Color,
    val surface: Color, val text: Color, val textSecondary: Color, val outline: Color,
)

val VegaNight = VegaPalette(
    listening = Color(0xFF22D3EE), thinkingStart = Color(0xFF8B5CF6), thinkingEnd = Color(0xFF3B82F6),
    speaking = Color(0xFF14B8A6), success = Color(0xFF22C55E), warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444), surface = Color(0xEB111827) /* 92% */, text = Color(0xFFF9FAFB),
    textSecondary = Color(0xFF9CA3AF), outline = Color(0x14FFFFFF) /* 8% white */,
)

val VegaDay = VegaPalette(
    listening = Color(0xFF0891B2), thinkingStart = Color(0xFF7C3AED), thinkingEnd = Color(0xFF2563EB),
    speaking = Color(0xFF0D9488), success = Color(0xFF16A34A), warning = Color(0xFFD97706),
    danger = Color(0xFFDC2626), surface = Color(0xF0F8FAFC) /* 94% */, text = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569), outline = Color(0x140F172A) /* 8% ink */,
)

// Noto Sans files in res/font; Devanagari falls back to the system Noto Sans Devanagari
val NotoSans = FontFamily(
    Font(R.font.noto_sans_regular, FontWeight.Normal),
    Font(R.font.noto_sans_medium, FontWeight.Medium),
    Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
)

object VegaType {
    val Greeting  = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp)
    val CardBody  = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal,   fontSize = 32.sp, lineHeight = 42.sp)
    val Caption   = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Medium,   fontSize = 30.sp, lineHeight = 38.sp)
    val CardTitle = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val Button    = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val Toast     = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Medium,   fontSize = 24.sp)
    val Hint      = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal,   fontSize = 22.sp)
}

object VegaShape {
    val Card = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(50)
}

object VegaSize {
    val OrbIdle = 56.dp
    val OrbActive = 120.dp
    val OrbCompanion = 300.dp
    val EdgeMargin = 24.dp
    val CardMaxWidth = 760.dp
    val CardPadding = 24.dp
    val MinTouch = 76.dp
    val ButtonMinWidth = 160.dp
    val Gap = 16.dp
}

object VegaMotion {
    const val WakeMs = 250
    const val CardMs = 220
    const val ThinkingTurnMs = 2200
    const val SuccessFlashMs = 300
    const val ToastVisibleMs = 2500
    const val CaptionHideMs = 3000
}
```

Use `VegaNight` by default and switch to `VegaDay` from the head unit's headlight signal, or by time when that signal isn't available.

Before day 1

## Day 0 setup

1. **Install the tools**

   Android Studio, SDK 34, NDK, CMake, JDK 17, Python 3.11, Git, scrcpy.
2. **Turn on developer mode on the head unit**

   Settings → About → tap the build number 7 times → Developer options → USB debugging on. Some units hide this behind a “Factory” password.
3. **Connect over adb**

   Check the unit appears, then switch to Wi-Fi adb for in-car work.
4. **Create the GitHub repository**

   Connect it to the Claude session so code can be pushed and reviewed.
5. **Prepare the accounts and phone**

   Google, Spotify and WhatsApp (linked device) on the unit; a second phone for test calls and messages.

Connect and check the head unit

```
adb devices
adb tcpip 5555 && adb connect <unit-ip>:5555
scrcpy

adb shell getprop ro.build.version.release        # expect 14
adb shell getprop ro.board.platform               # expect bengal
adb shell getprop ro.build.fingerprint            # "test-keys" means we can build system apps
adb shell cat /proc/cpuinfo | grep -i -m1 features
adb shell cat /proc/meminfo | head -3
adb shell pm list packages | grep -iE "bt|bluetooth|can|mcu|radio|car"
adb logcat | grep -iE "mcu|canbus|acc|keycode"    # press steering keys, turn the AC knob
```

LLM speed test on the head unit

```
git clone https://github.com/ggml-org/llama.cpp && cd llama.cpp
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DGGML_OPENMP=OFF -DLLAMA_CURL=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build-android -j --target llama-bench
adb push build-android/bin/llama-bench /data/local/tmp/
adb push qwen-0.8b-q4_k_m.gguf /data/local/tmp/
adb shell "cd /data/local/tmp && ./llama-bench -m qwen-0.8b-q4_k_m.gguf -t 4 -p 64,256 -n 32"
```

Day by day

## 15-day plan

Tick tasks as you finish them. Progress is saved in this browser only.

Every day

## Daily testing

1. **Typed commands first**

   A debug screen lets you type a command instead of speaking. It tests the brain and actions in seconds.
2. **Then speak 20 commands on the unit**

   Parked, music at medium volume. Note anything wrong with its trace ID.
3. **Read the timing log**

   Each command logs wake → speech end → text → decision → action → voice. Watch the total stay under 2 s.
4. **Send failures back**

   Push the log file and the exact words said. Every failure becomes a test sentence and, where useful, training data.

### Day 13 in-car test matrix

| Condition | Values to cover |
| --- | --- |
| Speed | Parked · city 30–40 km/h · highway 80–100 km/h |
| AC fan | Off · medium · max |
| Music | Off · 40% · 70% |
| Windows | Closed · driver window half open |
| People | Driver alone · two people talking |

Pass

At least 85% of the 100 scripted commands succeed, median latency under 2 s, and zero messages sent without confirmation.

Day 15

## Demo script

1. Car starts → Good morning Niharika!
2. Hey Vega, play Hanuman Chalisa on YouTube → results shown → number one
3. Pause · play · next video
4. Take me to Pune railway station
5. Find the nearest petrol pump → the second one
6. Call Rohan
7. Message Rohan I'm coming late → add that I'll reach by 7 → send it
8. Incoming WhatsApp message with privacy ON, then OFF
9. It's too hot → simulated AC turns on at 22°
10. How are you?
11. Car switches off → Bye Niharika, have a great day!

If something goes wrong

## Risks and plan B

| Risk | Found on | Plan B |
| --- | --- | --- |
| Qwen too slow on this chip | Day 1 | More simple-command rules, Qwen3-0.6B, shorter output |
| No system-app access (can't close apps or toggle Wi-Fi) | Day 1 | Go to the home screen; open the Wi-Fi panel for the user to tap |
| Built-in mic too poor in the car | Day 3 | External mic in the headliner |
| Vega hears its own voice | Day 4 | Pause the wake word while speaking; echo cancellation later |
| WhatsApp send automation breaks | Day 10 | Pre-fill the message; the user taps send |
| YouTube result picking breaks after an app update | Day 7 | Keep the app version fixed on the test unit; tap to choose |
| Fine-tune not better than the base model | Day 11 | Keep the base model with examples in the prompt |

Roadmap

## After day 15

| Next step | Technology |
| --- | --- |
| Real car control | Vendor CAN-box SDK, vehicle layer with safety rules |
| Custom “Hey Vega” wake word | openWakeWord trained on synthetic and real Indian voices |
| Better speech recognition | Zipformer fine-tuned on Indian-English in-car recordings |
| Weather, web search, bigger model | FastAPI + Redis + Postgres on one small server |
| Car-manual Q&A | SQLite full-text search + sqlite-vec + bge-small |
| Offline nearby places | OpenStreetMap data extracted with osmium |
| Better voices | Kokoro, then a custom brand voice |
| Updates in the field | Signed over-the-air model and app packages |

Library

## Reference documents

The full planning set, in the `vega-plan` folder.

`00_Vega_Master_Plan.md`Features, add-ons, technology design, 28-week plan

`01_Vega_UI_Design.md`Orb, captions, cards, toasts, screen flows

`02_Vega_Build_Procedure.md`Stage-by-stage build with checks

`03_Vega_Testing_Procedure.md`Six test levels, in-car scripts, safety suite

`04_Vega_Wake_Word_Training.md`Training “Hey Vega” from prototype to production

`05_Vega_STT_Report.md`Speech-to-text choice, hotwords, fine-tuning

`06_Vega_TTS_Report.md`Voices, Indian pronunciation, latency

`07_Vega_Feature_List.md / .csv`183 features with IDs and priorities

Vega · Implementation Guide v1.1 · Project lead Niharika · 24 October 2026