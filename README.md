# Vega

Offline voice assistant for an Android 14 car head unit. See `Vega Implementation Guide.md` and `docs/Vega_Features.md`.

## Build

1. Android Studio → **Open** → this folder. Let it sync (it downloads Gradle 8.11.1 and SDK 35 if missing).
2. Run the `app` configuration on the head unit or a phone (Android 14+).
3. Unit tests: right-click `app/src/test` → **Run tests**.

## First run on the device

1. Open Vega → **Permissions** → allow all.
2. **Allow overlay** → enable Vega.
3. **Start Vega**. The orb appears bottom-right. Tap it and speak, or type a command on the setup screen.

## Test from a PC

```
adb shell am start-foreground-service -n com.vega/.service.VegaService -a com.vega.action.TEXT --es text "take me to pune railway station"
adb shell am start-foreground-service -n com.vega/.service.VegaService -a com.vega.action.TALK
adb pull /sdcard/Android/data/com.vega/files/vega-trace.log
adb logcat -s Vega
```

## Code map

- `llm/`: command format, parser, fixed-phrase rules, `PatternLlm` (stand-in for Qwen)
- `brain/`: `Assistant` state machine, `Brain` (rules → LLM), contact matching, reply templates
- `actions/`: apps, system, media, navigation, calls, messages, simulated car
- `speech/`: speech-to-text and text-to-speech interfaces with Android implementations
- `service/`: foreground service, boot receiver
- `ui/`: orb overlay, setup screen, theme

## Still to come

- sherpa-onnx: wake word, VAD, Zipformer speech-to-text, Piper voices (swap in behind `SpeechToText` / `TextToSpeechEngine`)
- llama.cpp + Qwen (swap in behind `LlmEngine`)
- AccessibilityService for "number two" on YouTube / Maps results
- NotificationListenerService for message announcements and WhatsApp
- Incoming call announcements
