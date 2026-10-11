# Jarvis for Android

A WebView app. Voice input uses Android's own SpeechRecognizer and replies use Android TextToSpeech, so it works without Chrome.

## Get the APK (no Android Studio needed)
1. Create an empty repository on github.com.
2. Upload every file in this folder, keeping the folders. The hidden `.github/workflows/build-apk.yml` file must be included.
3. Open the repo's **Actions** tab. The "Build APK" run starts by itself (about 3 minutes). Use "Run workflow" if it does not.
4. Open the finished run, download the **Jarvis-APK** artifact, unzip it, and install `app-debug.apk` on your phone (allow "install unknown apps" when asked).

## Or build locally
Open this folder in Android Studio and choose Build > Build APK(s). Min Android 8.0.
from google import genai

client = genai.Client()

response = client.models.generate_content(
    model="gemini-flash-latest",
    contents="Explain how AI works in a few words",
)

print(response.text)