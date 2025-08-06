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

## 🚀 Getting Started (For Developers)

Follow these instructions to get a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

*   Android Studio installed on your machine.
*   A physical Android device (API 26+) with at least 4GB of RAM.
*   A quantized Gemma 3n `.task` model file (e.g., from the Gemma 3n E2B LiteRT Preview).

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
