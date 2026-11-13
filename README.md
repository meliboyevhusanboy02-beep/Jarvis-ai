# JARVIS AI — Android Voice Assistant (expanded starter)

Languages: Uzbek (`uz-UZ`), Russian (`ru-RU`), English (`en-US`).

## Included in this source package
- Kotlin Android application and Android 12+ manifest.
- Text-to-speech replies and speech recognition from the app.
- Experimental foreground speech-recognition loop which detects the spoken word “JARVIS” and responds “Eshitaman”.
- Persistent foreground notification for the microphone service.
- Dialer-role request flow.
- `InCallService` scaffold and incoming-call UI with answer/reject actions.
- Backend using OpenAI Responses API; API key remains server-side.
- Short conversation history persisted on-device and sent to backend.
- GitHub Actions workflow for debug APK.

## Important truth about the current build
This is a substantially expanded **development starter**, not a finished production assistant.
- The wake-word loop uses Android SpeechRecognizer. It is not a dedicated low-power DSP wake-word engine. It may stop, consume battery, require network, or be restricted by the LG firmware. Real-device testing is mandatory.
- Background microphone use must be started while the app is visible, with microphone permission and a persistent notification. Android/OEM battery management can stop it.
- The incoming-call UI and InCallService are scaffolding. The user must approve JARVIS as the default Phone app, and this implementation must be tested on the target phone/SIM before relying on it.
- The AI currently chats; it does not have unrestricted authority over Android. Add individual, permission-checked device actions and confirmation dialogs before enabling them.
- Uzbek TTS/speech recognition quality depends on speech services and language data installed on the phone.
- No app can bypass Android lock-screen, payment, password, or other secure-system protections.

## Build
1. Extract the ZIP.
2. Upload the *contents* (not the ZIP itself) to a new GitHub repository.
3. GitHub Actions builds `app-debug.apk`.
4. For AI responses, deploy `backend/` to a trusted HTTPS host.
5. Set the backend URL in `app/build.gradle.kts` `BASE_URL`.
6. Set `OPENAI_API_KEY` as a server environment variable. Never put it in Android code, APK, or GitHub.

## Backend deployment
- Install Node.js 20+.
- Run `npm install` in `backend/`.
- Set `OPENAI_API_KEY` securely in the hosting provider's environment.
- Optionally set `OPENAI_MODEL`.
- Run `npm start`.
- Verify `GET /health`.

## Security
Never store API keys in the APK. Do not give AI unrestricted shell or accessibility control. Keep device actions allowlisted and require confirmation for sensitive operations.

## Voice loop status
The current foreground service continuously re-arms Android SpeechRecognizer and looks for “JARVIS” / “Джарвис”. After the wake phrase, it accepts a follow-up phrase and sends it to the AI backend. This is experimental speech-recognition looping, not a certified always-on hotword detector; the recognition provider, battery policy, network, and LG firmware determine whether it stays active. Test with the phone locked before relying on it.
