# EyeGuardian: Your On-Device AI Safety Net

[![Video Demo](https://img.shields.io/badge/Watch-Video%20Demo%20(3%20min)-red?style=for-the-badge&logo=youtube)](https://YOUR_YOUTUBE_OR_VIDEO_LINK_HERE)
[![Latest Release](https://img.shields.io/github/v/release/FaresMallouli/Gemma3nEyeGuardian?label=Download%20Latest%20APK&style=for-the-badge)](https://github.com/FaresMallouli/Gemma3nEyeGuardian/releases/latest)

**EyeGuardian is an offline-first, multimodal safety monitor built for the Google Gemma 3n Impact Challenge. It transforms a standard Android phone into a proactive guardian that can detect falls and automatically call for help, without needing an internet connection.**

---

## 🎯 Impact & Vision: Accessible Safety for Everyone

Millions of people, especially the elderly and those with health conditions, live alone. A fall or a sudden medical emergency can be catastrophic if they can't reach a phone.

EyeGuardian addresses this critical real-world problem by providing an accessible safety net. Our vision is to leverage the privacy and power of on-device AI to grant independence to vulnerable individuals and peace of mind to their loved ones. By running entirely offline on affordable hardware, it provides a reliable solution that works for everyone, everywhere.

## 🧠 The AI Core: `Gemma3nHelper.kt`

**This is the single most important file for verification.** All direct interaction with the Gemma 3n model is encapsulated within this class. It proves our innovative use of the model's multimodal capabilities for two distinct tasks using the official Google AI Edge MediaPipe library (`com.google.mediapipe:tasks-genai:0.10.25`).

### Key Evidence:

*   **On-Device Model Loading:** The `loadModel()` function shows how the `.task` file is loaded from local storage into the `LlmInference` engine, configured to use the GPU backend for performance.
*   **Multimodal Logic:** The helper contains two separate analysis functions, proving the dual-use of the model:

```kotlin
// In: app/src/main/java/com/example/eyeguardian/helpers/Gemma3nHelper.kt

// --- TASK 1: Vision Analysis ---
suspend fun analyzeFrame(imageProxy: ImageProxy): String {
    // ...
    llmInferenceSession?.addQueryChunk(visionAnalysisPrompt) // The visual question
    llmInferenceSession?.addImage(mpImage) // The visual evidence
    val result = llmInferenceSession?.generateResponse() ?: "ERROR"
    return result
}

// --- TASK 2: Text Analysis ---
suspend fun analyzeTextResponse(userResponse: String): String {
    // ...
    val fullPrompt = textAnalysisPrompt + userResponse // The text question + user's response
    llmInferenceSession?.addQueryChunk(fullPrompt)
    val result = llmInferenceSession?.generateResponse() ?: "NEGATIVE"
    return result
}
```

This code directly validates that our demo is not faked and that we are using Gemma 3n for both advanced vision and text understanding tasks, entirely on-device.

### Design Philosophy: "Fail-Safe" by Default

A core principle of EyeGuardian is to **"fail safe"** prioritizing user safety above all else. This philosophy guided our implementation of the voice check-in feature.

*   **Gemma 3n is 100% Offline:** The core AI logic analyzing video frames and classifying text with Gemma 3n is performed entirely on-device and requires no internet.

*   **The Voice Check-in Trade-off:** The interactive check-in currently uses Android's built-in `SpeechRecognizer` service. While reliable, this service often requires an internet connection for the highest accuracy. This presents a challenge for users who are either unable to be online or who prefer not to use cloud based recognition for privacy reasons.

*   **Our Safety-First Solution:** We have engineered the system to treat **any failure or ambiguity in the voice check-in process as a potential emergency.** This directly addresses both key scenarios:
    1.  **For users without internet:** If the `SpeechRecognizer` service cannot be reached, the system doesn't wait.
    2.  **For users who value absolute privacy:** If a user has disabled network access, the result is the same.

    In all cases where a clear, positive response is not received whether due to no internet, background noise, or an unintelligible reply the app defaults to the safest possible action: **it immediately escalates to `CODE_RED` and sends the alert.**

This ensures that we never risk a "false negative" (missing a real emergency). **The safety of the user is the primary, non-negotiable objective.**


## 📂 Project Structure

To help judges navigate our repository, here is a map of the most important files.

```
Gemma3nEyeGuardian/
└── app/
    └── src/
        └── main/
            ├── java/com/example/eyeguardian/
            │   ├── helpers/
            │   │   ├── AppPreferences.kt # Manages saving user settings
            │   │   └── Gemma3nHelper.kt  # 🧠 CORE AI LOGIC - All Gemma 3n interaction is here
            │   ├── BaseActivity.kt       # Handles language switching for all screens
            │   ├── MainActivity.kt       # The main configuration and user setup screen
            │   └── VideoAnalysisActivity.kt # The real-time monitoring screen (Camera, TTS, STT)
            │
            ├── res/
            │   ├── layout/               # XML files for UI design
            │   ├── values/
            │       └── strings.xml       # Contains all user-facing text, including AI prompts
            │   
            │
            └── AndroidManifest.xml       # Declares permissions (Camera, SMS) and activities
```
---

### Future Roadmap: Evolving with Gemma & MediaPipe

Our choice to use Android's native STT and to analyze individual image frames was a deliberate, strategic decision based on the current, most stable features of the on-device AI ecosystem. We built EyeGuardian with the future in mind.

Our app currently uses Gemma's powerful image analysis to understand the scene by processing static frames. This is because full, real-time **video** and **audio** stream support for models like Gemma in frameworks like MediaPipe is still evolving.

Our application architecture is specifically designed to be modular. As the on-device toolchain matures, we can seamlessly upgrade our core components.

**Upcoming Enhancements:**

1.  **Fully On-Device Audio Analysis:** As soon as MediaPipe's support for Gemma's audio modalities is production-ready, we will replace the current STT component. This will unlock:
    *   **True 100% Offline Operation:** Complete independence from internet connectivity.
    *   **Advanced Audio Understanding:** Going beyond simple transcription to analyze the *tone* of voice (e.g., detecting stress or pain).

2.  **On-Device Video Stream Analysis:** The next major leap will be to move from single-frame analysis to processing video streams directly. This will enable:
    *   **True Motion Detection:** The model will be able to understand the *action* of falling, rather than just the state of being on the floor.
    *   **Movement & Sound Correlation:** By processing both video and audio streams simultaneously, the AI could accurately distinguish between a person falling (`CODE_RED`) and a person simply lying on the floor to exercise while music is playing (`CODE_GREEN`).

By starting with a robust, fail-safe system today, we have built the perfect foundation to incorporate these next-generation on-device features tomorrow, making EyeGuardian an even more intelligent and reliable guardian.

---
## 🚀 Getting Started (For Developers)

Follow these instructions to get a copy of the project up and running on your local machine for development and testing purposes.


### Installation

1.  **Clone the repository**
    ```sh
    git clone https://github.com/FaresMallouli/Gemma3nEyeGuardian.git
    ```
2.  **Navigate to the project directory**
    ```sh
    cd Gemma3nEyeGuardian
    ```
3.  **Open in Android Studio**
    *   Launch Android Studio.
    *   Select `File > Open...` (or `Open` on the welcome screen).
    *   Navigate to the `Gemma3nEyeGuardian` folder you just cloned and open it.
    *   Android Studio will automatically handle the rest (Gradle sync, etc.).

4.  **Run the App**
    *   Once the project is successfully synced, you can build and run it on your connected Android device.
