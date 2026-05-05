# DAMTest

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="120" alt="DAMTest logo"/>
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-API%2026%2B-green?logo=android" alt="API 26+"/></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin" alt="Kotlin"/></a>
  <a href="https://firebase.google.com"><img src="https://img.shields.io/badge/Firebase-Realtime%20DB-FFCA28?logo=firebase&logoColor=black" alt="Firebase"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License"/></a>
</p>

> **DAMTest** is an Android quiz app for first-year DAM (Desarrollo de Aplicaciones Multiplataforma) students in Spain. It covers all 8 core subjects of the curriculum, provides AI-powered feedback on wrong answers, and lets students read the official topic notes in-app — all with offline support.

---

## ✨ Features

- **8 DAM subjects** — Programación, Base de Datos, Sistemas, Lenguaje de Marcas, Entornos de Desarrollo, Digitalización, IPE, and Sostenibilidad.
- **Per-topic tests** (10 questions) and **General Test** mode (20 random questions across all topics).
- **Shuffled answer options** on every attempt to prevent memorisation.
- **AI explanations** — Gemini 2.5 Flash explains why an answer is wrong, shown in the Review screen.
- **PDF study notes** — 152 topic PDFs hosted as GitHub Release assets, downloaded on demand and cached locally.
- **Context/case questions** — support for multi-question blocks that share a common case statement.
- **Progress tracking** — per-topic score, number of attempts, and global dashboard stats.
- **Offline-first** — questions are cached locally with Room; Firebase sync only downloads what has changed (version-based diff).

---

## 🏗 Architecture

```
┌──────────────────────────────────────────────────────┐
│                     UI Layer                         │
│  MainActivity · TopicSelectionActivity · QuizActivity│
│  ReviewActivity · SubjectAdapter · TopicAdapter      │
│              QuizViewModel (StateFlow)               │
└────────────────────────┬─────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────┐
│                  Domain / Repo Layer                  │
│          QuizRepository  ·  FirebaseSyncManager      │
└──────┬──────────────────────────────────┬────────────┘
       │                                  │
┌──────▼──────────┐            ┌──────────▼───────────┐
│  Local (Room)   │            │  Remote (Firebase)   │
│  questions      │            │  Realtime Database   │
│  topic_progress │            │  (version-based sync)│
└─────────────────┘            └──────────────────────┘
```

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| Architecture | MVVM + Repository |
| Async | Coroutines + StateFlow |
| Local DB | Room 2.6 |
| Remote DB | Firebase Realtime Database |
| AI (explanations) | Gemini 2.5 Flash (`google-generativeai`) |
| PDF download | OkHttp 4 |
| DI | Manual (ViewModelFactory) |
| Min SDK | API 26 (Android 8.0) |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1) or newer
- A Google Firebase project with **Realtime Database** enabled
- A **Gemini API key** (free tier available at [Google AI Studio](https://aistudio.google.com))

### 1 — Clone and open

```bash
git clone https://github.com/alfonsonavio/DAMTest.git
cd DAMTest
```

Open the project in Android Studio.

### 2 — Firebase setup

1. Create a project at [Firebase Console](https://console.firebase.google.com).
2. Add an Android app with package `com.navio.damtests`.
3. Download `google-services.json` and place it at `app/google-services.json`.
4. In the Firebase Console, enable **Realtime Database** and import your questions
   following the schema described in [Database Schema](#-database-schema).

### 3 — API keys

Create (or edit) `local.properties` in the project root and add:

```properties
GEMINI_API_KEY=your_gemini_api_key_here
```

> `local.properties` is listed in `.gitignore` and must **never** be committed.

### 4 — Build & run

```bash
./gradlew assembleDebug
```

---

## 🗄 Database Schema

Firebase Realtime Database structure:

```
root/
├── versiones/
│   └── {subjectId}/          e.g. "base_de_datos"
│       └── {topicId}: Int    e.g. "tema_1": 3
└── preguntas/
    └── {subjectId}/
        └── {topicId}/
            └── p1/
                ├── text: "..."
                ├── contextText: "..." (optional)
                ├── optionA: "..."
                ├── optionB: "..."
                ├── optionC: "..."
                ├── optionD: "..."
                └── correctOptionIndex: 0
```

Topic ID conventions: `tema_1`, `tema_2`, …, `caso_1`, …, `repaso_1`.

---

## 📄 PDF Study Notes

PDF files are hosted as assets in the [GitHub Release v1.0](https://github.com/alfonsonavio/DAMTest/releases/tag/v1.0).

**Naming convention:** `{subjectId}_{topicNumber}.pdf`  
Example: `base_de_datos_1.pdf`, `programacion_10.pdf`

The base URL is defined in `Constants.kt`. To update it after publishing a new release, change `PDF_BASE_URL` to point to the new tag.

### Publishing / updating PDFs

```bash
# Install the GitHub CLI if not already installed: https://cli.github.com

# Initial release (152 files)
gh release create v1.0.0 RECURSOS_APP/*.pdf \
  --repo alfonsonavio/DAMTest \
  --title "v1.0.0 — Initial Release" \
  --notes-file CHANGELOG.md

# When new PDFs arrive — add them to an existing release
gh release upload v1.0.0 RECURSOS_APP/new_subject_1.pdf RECURSOS_APP/new_subject_2.pdf \
  --repo alfonsonavio/DAMTest

# Or bump to v1.1.0 and update Constants.PDF_BASE_URL accordingly
gh release create v1.1.0 RECURSOS_APP/*.pdf \
  --repo alfonsonavio/DAMTest \
  --title "v1.1.0 — Added 8 new topic PDFs"
```

---

## 🔒 Security Notes

| Item | Approach |
|------|---------|
| `google-services.json` | Must be added locally; listed in `.gitignore` |
| `GEMINI_API_KEY` | Stored in `local.properties`; injected as `BuildConfig` field |
| Firebase DB rules | Set `read: true` for questions; restrict write to authenticated admins |

---

## 📁 Project Structure

```
app/src/main/java/com/navio/damtests/
├── ai/
│   └── GeminiExplainer.kt        # Gemini AI integration
├── data/
│   ├── local/
│   │   ├── db/AppDatabase.kt     # Room database singleton
│   │   └── entity/               # Question, TopicProgress, DAO, etc.
│   └── (FirebaseManager.kt)      # Superseded — use FirebaseSyncManager
├── ui/
│   ├── SubjectAdapter.kt
│   ├── TopicAdapter.kt
│   └── viewmodel/
│       ├── QuizViewModel.kt
│       ├── QuizViewModelFactory.kt
│       └── QuestionResult.kt
├── Constants.kt                  # PDF_BASE_URL and other app-wide constants
├── FirebaseSyncManager.kt        # Version-based Firebase → Room sync
├── MainActivity.kt               # Dashboard
├── QuizActivity.kt               # Quiz screen
├── QuizRepository.kt             # Data access abstraction
├── ReviewActivity.kt             # Results + AI explanation screen
├── ReviewAdapter.kt
├── ShuffledQuestion.kt           # Option shuffling logic
├── TestDataHolder.kt             # In-memory result transfer between activities
└── TopicSelectionActivity.kt     # Subject topic list + PDF download
```

---

## 🛣 Roadmap

- [ ] Groq / llama-3.3-70b integration for real-time wrong-answer feedback during the quiz
- [ ] Firebase Authentication (student accounts + cloud progress sync)
- [ ] Statistics screen with per-subject performance charts
- [ ] Dark mode support
- [ ] Notification reminders to study

---

## 👤 Author

**Alfonso Navío Castellano**  
Android Developer · [GitHub @alfonsonavio](https://github.com/alfonsonavio)  
2 years of professional experience in fintech (Quality Pay Systems)

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).
