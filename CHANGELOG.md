# Changelog

All notable changes to DAMTest are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Firebase Authentication** — users must log in before accessing the app.
  Supports email/password registration and Google Sign-In.
- `LoginActivity` — custom login screen with email/password fields, Google
  Sign-In button, and a "Forgot password?" dialog with its own email field
  that sends a Firebase password-reset email.
- `RegisterActivity` — registration screen with inline field validation:
  password must be at least 8 characters with a letter and a number, shown as
  permanent helper text and enforced with per-field error messages. On success
  the user is signed out and returned to login to sign in manually.
- `AuthManager` — singleton wrapping all Firebase Auth operations, returning
  `Result<T>` for clean error handling.
- `AuthUiHelper` — shows the app's custom card-styled dialogs (info, confirm,
  forgot-password) instead of default grey ones, and translates Firebase Auth
  error messages to Spanish.
- **Firestore progress sync** — after each test, progress is saved to Room AND
  to Firestore (`users/{uid}/progress/...`). On login, local and cloud progress
  are merged (most recent timestamp wins). Sync runs in the background and never
  blocks login.
- `UserProgressRepository` — Firestore read/write/merge for user progress.
- Custom header bar in `MainActivity` with a logout button and styled
  confirmation dialog.
- `QuestionsDao.getAllProgressOnce()` — one-shot query for cloud sync.
- `PdfReleaseRepository` queries the GitHub Release API to determine which topics
  have a PDF available, independently of whether they have questions in the DB.
- Unit tests with JUnit and MockK for `QuizViewModel`, `QuizRepository` and
  `UserProgressRepository.mergeProgress` (22 tests). `MainDispatcherRule`
  swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope`
  coroutines run synchronously in tests.
- **Smart review mode** ("Repaso inteligente") — a per-subject practice mode
  that prioritises the questions the user fails most, using weighted random
  sampling (spaced-repetition style: base weight + failure rate + novelty for
  unseen questions + staleness). Accessed via a dedicated entry in the topic
  list (topicId "-4"), keeping the general tests purely random.
- `QuestionStats` table tracking per-question seen/correct/wrong counts, keyed
  by a Firebase-derived `stableId` that survives re-syncs. Updated on every
  answer in all test modes.
- `SmartReviewSelector` — pure, unit-tested weighted-sampling algorithm
  (8 tests), plus repository and ViewModel wiring.

### Changed
- `LoginActivity` is now the launcher Activity; `MainActivity` requires an
  authenticated session.
- `QuizRepository.updateProgress()` transparently syncs to Firestore when a
  user is logged in.
- Loading states now show a clean card with a spinner instead of a grey overlay.
- Replaced `TestDataHolder` singleton with `Parcelable` Intent extras for passing
  quiz results between `QuizActivity` and `ReviewActivity`. `Question` and
  `QuestionResult` now implement `Parcelable` via `@Parcelize`. Eliminates the
  risk of stale data if the system recreates Activities from the back stack.
- Topic list now merges two sources: Room DB (topics with questions) and GitHub
  Release assets (topics with PDFs). A topic appears if it exists in either.
- PDF button shown only when a PDF actually exists for that topic in the release.
- Tapping the test button on a topic without questions shows a dialog instead of
  opening an empty quiz.
- Migrated Firebase project to professional account (alfonsonavio).
- `minFetchInterval` changed from 0 to 3600 — app is now production-ready
  in terms of Remote Config fetch frequency.
- Introduced Hilt for dependency injection. The Room database, DAO and
    `QuizRepository` are now provided as singletons via a Hilt module
    (`di/AppModule`), and Activities receive dependencies with `@Inject`
    instead of manual instantiation. `QuizViewModel` uses `@HiltViewModel`
    with `by viewModels()`, removing the manual `QuizViewModelFactory`
    (deleted) and the manual singleton in `AppDatabase`.
- Made `AuthManager` and `UserProgressRepository` injectable `@Singleton`
  classes (previously global `object`s), with `FirebaseAuth` and
  `FirebaseFirestore` provided via a new `FirebaseModule`. This removes hidden
  singleton dependencies from `QuizRepository`, making `updateProgress` fully
  unit-testable with mocked dependencies.
- `Question` now has a `stableId` ({subjectId}_{topicId}_{firebaseKey}) so
  per-question stats stay attached across syncs. Room bumped to v3.
- `FirebaseSyncManager` re-downloads a topic when its local cache is empty even
  if the version matches (recovers from destructive migrations), and now logs a
  single summary line instead of one per topic.
- Smart review is practice-only: it records per-question stats but does not save
  a test score, and its card shows "Practica tus fallos".

### Fixed
- Quiz resets on Activity recreation (screen off on aggressive OEM battery optimization,
  screen rotation). `loadQuestions()` is now guarded with `isEmpty()` so the ViewModel
  state is preserved across Activity recreations.
- Question counter not advancing correctly when first question was answered wrong,
  caused by the same Activity recreation issue above.
- Duplicate questions within a test prevented with `distinctBy { it.text }` after
  loading from Room.

### Configuration
- Firebase password policy set to "Enforce" (min 8 chars, lowercase + numeric)
  so the requirement applies to both registration and password reset.

---

## [1.0.0] — 2026-05-05

First public release. Covers the full first-year and second-year DAM curriculum.

### Features
- 8 DAM subjects: Programación, Base de Datos, Sistemas, Lenguaje de Marcas, Entornos de Desarrollo, Digitalización, IPE, and Sostenibilidad.
- Up to 19 topics per subject with 10 questions each.
- Three general test modes per subject: topics 1–10, topics 11–20, and all topics (20 random questions).
- Answer options shuffled on every attempt to prevent order memorisation.
- Context/case question support — questions may share a common case statement shown via a dedicated button.
- **Groq (llama-3.3-70b-versatile)** real-time feedback shown inline after each wrong answer, with the incorrect option highlighted in red and the correct one in green.
- **Gemini 2.5 Flash** detailed explanations for wrong answers in the Review screen.
- Both AI API keys managed via **Firebase Remote Config** — never stored in source code.
- **Firebase Realtime Database** version-based sync; only changed topics are downloaded.
- **Room** local cache with full offline support after first sync.
- **MVVM** architecture: `QuizViewModel` + `StateFlow` for reactive UI.
- PDF study notes (152 files) hosted as GitHub Release assets, downloaded on demand and cached locally.
- Per-topic progress tracking (last score, total questions, attempt count, timestamp).
- Global dashboard showing average score and total tests across all subjects.
- APK named `DAMTest-{versionName}.apk` instead of the default `app-debug.apk`.

### Architecture / technical
- `Constants.kt` centralises `PDF_BASE_URL`.
- `RemoteConfigManager` singleton handles Firebase Remote Config fetch with proper `await()` on settings before fetch, and split `fetch()` + `activate()` for reliability.
- `FastExplainer` reads the Groq API key fresh on every call to avoid caching an empty key.
- All `launchWhenStarted` calls replaced with `repeatOnLifecycle(STARTED)`.
- Status bar light-icon API uses `WindowInsetsController` on API 30+ with a `@Suppress("DEPRECATION")` fallback.
- `google-services.json` and `local.properties` excluded from version control.

### Bug fixes (found during pre-release testing)
- **ReviewAdapter: wrong questions shown as correct** — `isCorrect` was incorrectly recalculated as `userSelectedIndex == correctOptionIndex`, mixing shuffled-position and original-position coordinate systems. Fixed to use the pre-computed `res.isCorrect` field and `res.shuffledOptions` for the user's selected text.
- **Flash of clean question UI when finishing test** — pressing "Finalizar Test" briefly showed a clean question before the results dialog appeared, because `resetAnswerUI()` was called unconditionally when `currentAnswerState` became null. Fixed to skip the reset when `isTestFinished` is already true.
- **Groq key not loading from Remote Config** — `setConfigSettingsAsync` was not awaited before the fetch, so the minimum interval change had no effect. Fixed by awaiting settings, then calling `fetch(0)` and `activate()` separately.
- **GeminiExplainer personal content** — prompt contained hardcoded personal names and messages. Replaced with generic pedagogical prompt.
- **Firebase database URL hardcoded** — removed hardcoded Realtime Database URL; now reads from `google-services.json` automatically.
- **PDF URL pointing to personal repo** — `TopicSelectionActivity` hardcoded a raw GitHub URL to the personal repo `Navio13`. Now reads from `Constants.PDF_BASE_URL` pointing to the professional GitHub Release.

---

[Unreleased]: https://github.com/alfonsonavio/DAMTest/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/alfonsonavio/DAMTest/releases/tag/v1.0.0