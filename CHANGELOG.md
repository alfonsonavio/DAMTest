# Changelog

All notable changes to DAMTest are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Static analysis in CI** — every push and pull request runs detekt (with
  the ktlint formatting rules) and Android Lint before the unit tests, and any
  finding fails the build; the reports are uploaded with the run. Rules are in
  `config/detekt/detekt.yml`, and `gradlew detekt --auto-correct` fixes the
  formatting locally.
- **Varied streak reminders** — the streak warning now picks one of four
  messages ("¡No pierdas tu racha!", "Tu racha te espera", "¡Racha en
  peligro!", "Sigue sumando"), like the everyday reminders already did.
- **Daily study streak** — Home now shows the real number of consecutive days
  with at least one finished test (smart review included); it was a fixed
  placeholder ("🔥 5"). The streak is kept until the end of a day without
  tests, follows the device's local calendar days and syncs across devices
  with the attempt history. Smart review practice is now stored as an attempt
  (topic "-4") for the streak, but the statistics still ignore it.
- **Streak reminder** — if the user has a streak and hasn't studied yet that
  day, the daily reminder warns about losing it ("Llevas N días de racha").
- **Favourite questions** — a star in the quiz header and on every question of
  the review marks it as favourite. Each subject gets a "Preguntas favoritas"
  row to practise its favourites, and Home has a "Preguntas favoritas" card
  that opens a list of every favourite with its correct answer (removable,
  with undo) and a "Practicar favoritas" button that mixes all subjects. Like
  smart review, the favourites test counts for the streak and per-question
  stats but saves no score or topic progress (attempt topic "-5"). Favourites
  sync across devices (`users/{uid}/favorites`, last write wins) and are
  removed on logout and account deletion.
- **Exam mode** — a "Modo examen" row in each subject opens a timed exam:
  choose the topics (all, 1-10 or 11-20 when the subject has both halves),
  the size (10, 20, 30 or 40 questions, only those the topics can fill) and
  whether wrong answers subtract 1/3. The time is 1.5 minutes per question and
  keeps running in the background. There is no feedback until the exam is
  handed in: answers can be changed or cleared, questions flagged as doubtful
  and reached from a question grid. It is graded out of 10 on submission or
  automatically when the time runs out, then opens the usual review (blank
  questions marked as such, AI explanation available). An exam counts as a
  test in Statistics and for the streak (attempt topics "-6", "-7", "-8") but
  saves no topic progress.

### Changed
- **Data layer split by responsibility** — the single `QuestionsDao` (41
  queries) is now one DAO per table plus `UserDataDao` for the logout wipe,
  and `QuizRepository` keeps only the question bank and the tests built from
  it: the user's progress, answers, attempts, streak and statistics moved to
  `ProgressRepository`, and favourites to `FavoritesRepository`. Logout and
  account deletion share `CloudBackup` and `LocalUserData` instead of
  repeating the same steps.
- **Code clean for detekt** — every file follows the ktlint formatting rules,
  numbers have named constants or resources, long methods (Home, quiz screen,
  registration form, review list, question sync) are split into small ones,
  and only `suspendRunCatching` catches every exception, for the Firebase,
  Credential Manager and AI SDKs that don't document what they throw.
- **Material time picker for the reminder** — the reminder time uses the
  Material 3 clock picker, in the app's colours and always in 24 h format,
  instead of the old system dialog with its own pink accent.
- **Edge-to-edge on every screen** — the app now draws behind the status and
  navigation bars on every Android version (Android 15 already forced it),
  instead of the deprecated `statusBarColor`. Headers get the real status bar
  height instead of a guessed 44-52 dp, and the status bar icons are dark on
  light screens and light on the navy headers, switching with the bottom
  navigation tabs. This fixes the white, almost invisible status bar icons on
  light screens with Android 15.
- **No deprecated APIs or build warnings** — removed the pre-Android 8
  vibration path (min SDK is 26) and the old status bar flags, moved the
  build to `kotlin.compilerOptions` and `base.archivesName` (APKs are now
  `DAMTest-<version>-<type>.apk`) instead of the internal variant API, removed
  the unused view binding, and Room exports its schema to `app/schemas` with
  the Room Gradle plugin so future migrations can be verified.
- **Every user-facing text in `strings.xml`** — about 300 texts written in
  Kotlin or in layouts now live in resources, with plurals where the number
  changes the sentence ("1 pregunta en blanco" / "3 preguntas…"). Catalogs,
  rules and the reminder decide their text without a Context through a small
  `UiText` type, so they stay unit-tested. Layout placeholders that are always
  replaced at runtime became `tools:text`, and unused strings were removed.
- **English names in code** — the last Spanish identifiers (`explicarRapido`,
  `btnVerExplicacion`, `btnReportar`, `btnFavorito`) were renamed; Firebase
  auth errors are a typed `AuthError` (no more checking whether the message
  contains "correo"), and report reasons a typed enum with the same stored
  values.
- **One model for the special tests** — the general tests, smart review,
  favourites and exams are a `TestMode` enum instead of "-1"…"-8" strings
  scattered across the code (the stored ids are unchanged and covered by a
  test). Exams are described by a parcelable `ExamConfig`, and every screen
  that is opened with data (quiz, review, exam, subject) builds its own
  `Intent` with private extra keys, so no key is typed twice.
- **Packages organised by feature** — no class is left in the root package
  except the Application. Screens live in `ui/<feature>` (auth, main, quiz,
  review, exam, favorites, settings, subjects, topics…), data in
  `data/<area>` (quiz, sync, catalog, pdf, report, settings, local/dao…),
  shared helpers in `core`, and the tests mirror the same packages. No
  behaviour changes.
- **Dependencies cleaned up** — every dependency and plugin is now declared in
  the version catalog (`gradle/libs.versions.toml`). Removed what the app
  never used: Jetpack Compose (no screen uses it), the Crashlytics build
  tools (wrongly added as an app library), CardView, Turbine, the duplicated
  Material 1.11 declaration and the template instrumented test.
- **Unused resources removed** — two layouts, six drawables, two menus and
  the template navigation strings.
- **Subject screen redesigned** — the practice modes (exam, smart review,
  general test and favourites) are now cards in a "Practicar" block at the
  top, always in view, instead of rows at the end of the topic list. Smart
  review and favourites show how many questions they have; the general test
  asks for all topics or a half instead of taking three rows. Topics are
  compact rows with their last score, a progress bar and the PDF button.
  The screen now has a ViewModel and lives in `ui/topics`.
- **Room database v5 with a real migration** (`MIGRATION_4_5`) adding the
  `favorite_questions` table, so updating keeps the local history instead of
  rebuilding the database.
- **Google sign-in migrated to Credential Manager** (`androidx.credentials` +
  Sign in with Google), replacing the deprecated Google Sign-In for Android
  library (`play-services-auth` / `GoogleSignInClient`). The ID token is still
  exchanged with Firebase Auth, so existing accounts are unaffected. Logout and
  account deletion also clear the stored credential state. If the device has no
  Google account, a clear message is shown; closing the picker shows nothing.

### Fixed
- **Closing a screen no longer reports a failure** — the background work
  (sign-in, sync, backups, explanations) caught every exception, including the
  cancellation a coroutine gets when its screen closes, and went on as if it
  had failed. Cancellation now always stops the work.
- **A topic is never left half-updated** — a new version of a topic's
  questions replaces the old one in a single database transaction.
- **Aligned options in the review** — the first option lost its indentation
  when it was neither the right answer nor the user's choice.
- **Reminder picker opens on the clock** — the Material time picker could
  open on the keyboard input; it now always starts on the clock.
- **Tests always have their full length** — a test of 20 sometimes had 19
  questions: some questions appear in more than one topic, and duplicates
  were removed after picking the 20. The database now picks different
  questions directly, and the exam counts only different questions when it
  offers its sizes.
- **Subject screen details** — the back arrow was dark grey on the navy
  toolbar, and the topic and "continue" progress bars drew a stray dot at
  their end (Material 3's stop indicator); both are gone.
- **Exam plurals and contrast** — the question grid and the result said "1
  dudosas" or "1 aciertos"; each count now has its own plural. The navy
  "Entregar examen" button showed purple text on navy in the grid sheet; navy
  buttons now always use light text.
- **Reminder minutes** — the daily reminder only kept the hour: choosing 6:45
  scheduled it at 6:00 and Settings showed "06:00". The minutes are now saved,
  shown and used; reminders set before keep their time at minute 0. The delay
  to the next reminder uses java.time, so daylight saving changes are handled.
- **Review explanations no longer hang** — the "Ver explicación" request waits
  at most 20 seconds for Gemini, retries once when Gemini is busy (503 "high
  demand") and otherwise falls back to Groq with the same prompt, so there is
  almost always an explanation. Closing the dialog cancels the request in
  flight, and after 8 seconds the dialog says it is taking longer.

## [1.1.2] — 2026-10-23

### Added
- **New logo** — `< ✓ >` (code brackets around a check) on the navy brand
  colour: adaptive launcher icon with monochrome variant, login logo and a
  proper single-colour notification icon.
- **Statistics cloud sync** — test attempts and per-question stats are now
  stored in Firestore (`users/{uid}/attempts`, `users/{uid}/questionStats`), so
  Statistics survive reinstalls and device changes. New data is uploaded as it
  happens; on startup the app merges the cloud and local history (full merge
  the first time on a device, then only what changed).
- **Safe logout** — before signing out, all progress, statistics and the test in
  progress are compared with the server copy, missing data is uploaded and every
  write is confirmed. Only then is the user's local data wiped. Without a
  confirmed backup (e.g. no connection) nothing is deleted and the user stays
  signed in. Everything is restored on the next login.
- **New version notice** — when `latest_version_code` in Remote Config is newer
  than the installed version, a dialog (at most once a day) links to the latest
  release.
- **Delete account** (Settings) — deletes the user's Firestore data, then the
  account, then the local copy. Requires a recent sign-in; if anything fails
  before the data is deleted, nothing is deleted.
- **Report a question** (review screen) — wrong answer, typos or other problem,
  with an optional comment, sent to the Firestore `reports` collection.

### Fixed
- Finishing a test and leaving the results quickly (or finishing it offline)
  lost the attempt: it was missing from the "Tests" count and the evolution
  chart. The attempt is now recorded locally before anything touches the
  network.
- Topic progress uploads no longer wait for the server, so they can't hang
  offline or be cancelled when the quiz screen closes; Firestore queues them.
- The detailed AI explanation (review) explained the wrong option most of the
  time: it received the position of the answer in the shuffled order but listed
  the options in their original order. It now gets the options as shown and
  refers to them by the same letters (a–d) the review screen uses.
- The quiz progress bar stopped at the start after rotating the phone.
- Leaving the app or rotating the phone during a test reshuffled the options,
  so an answered question was highlighted in the wrong place and the vibration
  and the AI request were repeated. The question state now lives in the
  ViewModel.
- The results dialog could appear twice after returning to the app.
- The instant AI explanation of a question could appear on the next one if the
  user moved on before it arrived.
- General tests are saved under their own id (e.g. "-1") instead of the topic
  of their first question, which overwrote that topic's score and never showed
  a score on the general test rows.
- "Test general (temas 1-10 / 11-20)" is only offered when the subject has
  questions in both halves; a test with no questions now says so instead of
  staying on "Cargando…" forever.
- First launch: subjects no longer show "Próximamente" while the questions are
  still downloading; the app waits for the download.
- PDF list: stored on disk and refreshed at most once a day, with timeouts, so
  GitHub's 60-requests-per-hour limit (e.g. a whole class on one Wi-Fi) or a slow
  network no longer hides the PDFs. Interrupted PDF downloads are discarded
  instead of being kept as a broken file.
- Internal ids ("caso_1", "repaso_1", "MARCAS") replaced by readable names on
  Home, Statistics and the topic screen title.
- The topic list keeps its scroll position when coming back from a test.
- Statistics refresh when the screen is shown again and when a background sync
  finishes; the "Tests" figure now matches Home and Subjects.
- One reusable Groq client (it created a new, never-closed one per wrong answer)
  with request logging turned off.
- Texts: "1 test" / "1 pregunta" singulars, a reminder that mentioned a streak
  the app doesn't have, and friendly messages instead of raw AI error text.

## [1.1.1] — 2026-10-23

### Fixed
- AI explanations stopped working because the hardcoded models were retired
  (Groq `llama-3.3-70b-versatile`, Gemini `gemini-2.5-flash`).

### Changed
- AI model names are now read from Firebase Remote Config (`groq_model`,
  `gemini_model`), so a retired model can be replaced from the console without
  an app update. Defaults: `openai/gpt-oss-120b` and `gemini-3.5-flash`.

## [1.1.0] — 2026-10-22

Redesigned app (Material 3, dark mode, bottom navigation) with accounts and
cloud progress sync, statistics, settings, smarter review, resumable tests and
the real second-year subjects.

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
- Haptic feedback on answering: a soft tick for correct answers and a stronger
  double buzz for wrong ones, via a toggleable `HapticFeedbackManager` (ready
  for a future settings toggle). Requires the VIBRATE permission.
- Slide-in animation: the question content slides in from the right on each new
  question, while the progress bar and next button stay fixed.
- **Study reminder notifications** — a daily reminder (18:00) scheduled with
  WorkManager, showing a rotating motivational message that opens the app when
  tapped. Scheduled only for logged-in users and cancelled on logout.
  Requests the POST_NOTIFICATIONS permission at runtime on Android 13+.
  Reminder hour and on/off are centralised for a future settings screen.
- **Visual redesign — design system.** New color palette (navy + periwinkle +
  raspberry) with semantic color names, bundled Poppins (titles) and Inter
  (body) fonts with a `TextAppearance.DAMTest.*` type scale, and a unified
  `Theme.DAMTest`. Subject pastels recalibrated.
- **Bottom navigation.** Migrated to a Navigation Component architecture: a
  `NavHostFragment` + `BottomNavigationView` shell with Inicio / Asignaturas /
  Estadísticas destinations. `MainActivity` is now a lightweight container.
- **Home screen (`HomeFragment`).** Redesigned dashboard with a time-of-day
  greeting, quick stats (streak, average, tests), a "continue where you left
  off" card, and a smart-review shortcut.
- **`SubjectsFragment`.** Subject grid with a 1º/2º course segmented control.
  Second-year subjects (Acceso a Datos, Desarrollo de Interfaces, Prog.
  Multimedia y Móviles, Prog. Servicios y Procesos, Sist. Gestión Empresarial,
  EIE) added as placeholders until their questions are uploaded to Firebase.
- Subjects with no downloaded content show a styled "coming soon" dialog instead
  of an empty topic screen. Availability is detected automatically from the local
  cache (`subjectHasContent`) — no manual flag to maintain.
- `StatisticsFragment` placeholder destination (charts come in a later branch).
- **Statistics screen.** Per-course (1º/2º) dashboard with KPIs (global accuracy,
  tests done, subjects studied), an evolution line chart (score per test over
  time, MPAndroidChart, smooth cubic with gradient fill), per-subject accuracy
  bars, and a "to review" list of the weakest topics. Fed by a new immutable
  `TestAttempt` history table and existing `QuestionStats`.
- `StatisticsCalculator` — pure, unit-tested statistics logic (10 tests).
- `TestAttempt` table recording each completed test (excludes smart review).
- **Settings screen** (`SettingsActivity`). Profile summary (name, email), a
  daily study-reminder toggle with a configurable hour (`TimePickerDialog`), a
  haptics toggle, and logout pinned to the bottom of the screen. Backed by a new
  `SettingsManager` that persists the preferences in SharedPreferences.
- **Dark mode.** A graphite-based Material 3 dark theme (`values-night`) that
  redefines every semantic color token, so all token-based screens adapt to the
  system automatically. A theme chooser (System / Light / Dark) in Settings,
  persisted in `SettingsManager` and applied app-wide at startup and on change
  via `AppCompatDelegate`.
- **Smart review from Home.** The "Repaso inteligente" card opens a subject
  picker that launches that subject's smart review. Only subjects with at least
  one failed question are listed; with none, a notice explains there is nothing
  to review yet.
- `SubjectCatalog` — single source of truth for the 14 subjects (id, name, icon,
  colour, course), replacing the lists duplicated in Home, Subjects and
  Statistics.
- **Resume a test in progress.** The current test is saved after every answer
  (questions in order, chosen answers with their shuffled options, and score) in
  `QuizSessionStore`, so leaving the quiz or the app being killed no longer loses
  it. Only one test is kept. Home's "Continúa donde lo dejaste" card shows it with
  its progress and resumes it directly; reopening that same test from the topic
  list or the smart-review picker asks whether to continue or start again
  (`QuizLauncher`). If its questions are gone after a re-sync, a new test starts
  instead. The saved test is cleared on finishing and on logout.
- **Real second-year subjects.** The 2º placeholders are replaced by the actual
  curriculum: Acceso a Datos (`acceso_datos`), Ciberseguridad (`ciberseguridad`),
  Desarrollo de Interfaces (`interfaces`), Inglés Profesional (`ingles`), IPE II
  (`ipe2`), Programación de Servicios y Procesos (`servicios`), Programación
  Multimedia y Dispositivos Móviles (`multimedia`) and Sistemas de Gestión
  Empresarial (`gestion`), each with its own icon. EIE is removed.

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
- `Subject` gains a `course` field (1 or 2), inferred in-app rather than stored
  in Firebase; both years share the same `preguntas/<subjectId>/` structure.
- Status bar is now navy and the home header reserves the status-bar inset via
  padding, so content no longer overlaps the system clock.
- Removed leftover template themes (values-v23) and consolidated to a single
  `Theme.DAMTest`.
- Removed the temporary compatibility color aliases (`colorPrimary`, `bg_screen`,
  `primary_dark`, `purple_700` and the `bg_*` subject colors) used during the
  redesign. Every layout and class now references the semantic design-system
  tokens directly.
- **Material 3 migration.** Migrated the whole UI foundation from Material
  Components (Material 2) to Material 3: `Theme.DAMTest` now extends
  `Theme.Material3.Light.NoActionBar`, the palette is mapped to M3 color roles
  (primary/secondary/tertiary containers, surface variant, outline and error
  roles), the `TextAppearance.DAMTest.*` scale and the custom button styles use
  their `Material3` parents, and the 18 inline `Widget.MaterialComponents`
  references across 8 layouts were repointed to `Widget.Material3`. The visual
  result is unchanged, and the migration provides the M3 color attributes that
  components such as `MaterialSwitch` require for the upcoming settings screen.
- The daily reminder and answer haptics now honor the user's saved preferences:
  the reminder is scheduled only when enabled and at the chosen hour (and
  rescheduled immediately when changed), and haptics respect the toggle.
  `ReminderScheduler.schedule` takes the hour and a replace flag. Logout, which
  the bottom-navigation redesign had dropped from the header, now lives in
  Settings and cancels the reminder on sign-out.
- Replaced hardcoded colors with semantic tokens across every remaining screen
  (auth, dialogs, list items, quiz, review, subjects, topics and the statistics
  chart) so they all follow the active theme. The quiz and topic-selection
  status bars and the topic-selection toolbar now use brand navy, and the
  subjects, statistics and review screens reserve the status-bar inset so titles
  no longer collide with the system bar.
- Bottom navigation hides the Material 3 active-indicator pill (only the icon and
  label take the periwinkle tint) and keeps its shadow without the dark-mode
  elevation tint. The review "Menú" button gains a periwinkle outline and a
  subtle fill so it reads as a button distinct from the bottom bar. The 1º/2º
  segmented control track uses an adaptive token instead of a fixed light colour.
- **Smart review prioritises failed questions.** Every question answered wrong
  at least once now enters the review first (weighted by failure rate and
  staleness when there are more than fit), and only the free slots are filled by
  the general weighting. Previously novelty could swamp them (8 failed questions
  in a 190-question subject appeared ~1.4 times per review). The topic-list entry
  follows the same rule and shows a notice for subjects with no failures. Normal
  tests are unaffected: they still pick questions at random in the DAO.
- A subject now opens when it has questions **or** at least one PDF, so PDFs can
  be published before the questions. General tests and smart review are listed
  only once the subject has questions.
- Turning the reminder on in Settings requests the POST_NOTIFICATIONS permission
  on Android 13+. If it is denied, the switch turns back off and a dialog offers
  to open the system notification settings.

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

[Unreleased]: https://github.com/alfonsonavio/DAMTest/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/alfonsonavio/DAMTest/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/alfonsonavio/DAMTest/releases/tag/v1.0.0