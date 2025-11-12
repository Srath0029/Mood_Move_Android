🌤️ MoodMove: Android Health & Mood Tracker
📄 Overview

MoodMove is a modern, data-driven Android health and wellness app built entirely with Jetpack Compose and Firebase.
It empowers users to track their physical activity, monitor mood fluctuations, and gain personalized insights through interactive visualizations — promoting sustainable wellbeing habits aligned with UN SDG 3: Good Health & Wellbeing.

The application integrates context-aware logic, using both weather data and step-count activity logs, to understand how external conditions influence daily mood and motivation.

✨ Key Features
🏠 Home Dashboard

Displays today’s date and a “Week at a Glance” summary of mood, exercise, and weather.

Features a custom-drawn chart visualizing weekly averages (mood, duration, temperature).

Includes a community feed carousel showing recent public activities (e.g., “Jane completed a run 5 min ago”).

🏃 Activity Logging

Intuitive interface for adding new activities: type, duration, mood, and date.

Validates entries and stores them securely in Firestore.

📜 History & Insights

Searchable list of all logged activities.

Edit or delete any record using interactive dialogs.

Dedicated Insights screen renders:

Mood trend line chart

Composite bar chart comparing exercise duration vs. weather

⚙️ User & Preferences

Manage profile information and app preferences (reminders, sync settings, etc.).

Seamless login/logout with Firebase Authentication.

🔔 Background Services
⏰ Precise Reminders (AlarmManager)

Configurable daily “Hydration” and “Medication” reminders.

Uses AlarmManager to schedule exact, time-critical alarms that trigger even when idle.

🔄 Intelligent Background Sync (WorkManager)

“Background Updates” toggle enables a ContextIngestWorker.

Worker fetches user location periodically (with constraints: network + battery).

Persists contextual data to a local Room database for later analytics.

🧱 Tech Stack & Architecture
Layer	Technology	Purpose
Language	Kotlin (100%)	Modern Android language
UI	Jetpack Compose	Declarative UI and state management
Architecture	MVVM + Repository Pattern	Clear separation of concerns
Database (Local)	Room	Persist background-collected data
Backend (Cloud)	Firebase Auth + Firestore	User auth & activity data
Background Tasks	WorkManager + AlarmManager	Scheduled jobs & reminders
Networking	Retrofit + OpenWeatherAPI	Weather-based context awareness
Navigation	Jetpack Navigation for Compose	Screen transitions
🧠 Core Design Principles

Reactive UI using Compose StateFlows.

Clean Architecture ensuring testability and scalability.

Non-blocking operations via Kotlin Coroutines.

Seamless offline support with Room + Firestore caching.

🧩 Folder Structure
Mood_Move_Android/
├── app/                   # Main Android app source
│   ├── data/              # Repository & Room database
│   ├── model/             # Data classes
│   ├── ui/                # Jetpack Compose screens & components
│   ├── viewmodel/         # MVVM ViewModels
│   └── worker/            # WorkManager tasks (ContextIngestWorker)
├── build.gradle.kts
├── gradle.properties
├── google-services.json
└── README.md

🌟 Highlights

Modern Android Development: 100 % Jetpack Compose & Kotlin.

Cloud-Connected: Firebase Auth + Firestore + OpenWeather API.

Smart Background Services: WorkManager + AlarmManager integration.

Interactive Data Visualizations: Custom-drawn charts and insights.

User-Centric Design: Personalization, accessibility, and responsive UI.

🚀 Future Improvements

🤖 Add machine-learning mood predictions based on weather + activity.

📈 Integrate Google Fit API for live step and heart-rate data.

💬 Enable community sharing and friend leaderboards.

🧘 Incorporate mindfulness reminders and stress-tracking features.

☁️ Deploy Firestore triggers for cloud analytics automation.

💡 Key Takeaways

Demonstrates end-to-end Android app development with Compose.

Integrates context awareness for holistic health tracking.

Combines design, analytics, and cloud technologies into a cohesive wellbeing platform.
