MoodMove: A Modern Android Health & Mood Tracker
MoodMove is a sophisticated health and wellness tracking application for Android, built entirely with Jetpack Compose. It empowers users to log daily physical activities, monitor mood fluctuations, and gain actionable insights through rich data visualizations.

✨ Key Features
The application is architected around four primary modules, supported by robust background services to deliver a seamless and reliable user experience.

Home Dashboard: A central hub providing quick navigation, today's date, and a "Week at a Glance" summary. It features a custom-drawn chart displaying the week's key metrics (average mood, total exercise duration, and average temperature).

Dynamic Activity Feed: The home screen includes an auto-scrolling carousel that displays recent public activities from other users in the community (e.g., "Jane completed a Run 5 minutes ago"), enhancing user engagement.

Activity Logging: An intuitive interface for users to log new physical activities, including type, start/end times, mood score, and date.

Comprehensive History: A searchable and filterable list of all past activity logs. Users can view, edit, or delete any historical entry through interactive dialogs.

Data-Driven Insights: A dedicated screen that visualizes the user's weekly data using custom-drawn charts, including a mood trend line chart and a composite bar chart comparing exercise duration against weather conditions.

User & Preference Management: A settings screen for managing user profiles and application preferences.

Background Services
Precise Reminders (AlarmManager): Users can enable daily "Hydration" and "Medication" reminders. The app leverages AlarmManager to schedule exact, time-critical alarms that trigger reliably, even when the device is idle.

Intelligent Background Sync (WorkManager): A user-toggleable "Background Updates" feature schedules a periodic ContextIngestWorker. This worker intelligently fetches the user's location in the background, subject to constraints like network connectivity and battery level, and persists the data locally.

🛠️ Tech Stack & Architecture
This project is built with 100% Kotlin and adheres to modern Android development best practices recommended by Google.

UI Framework: Jetpack Compose for a fully declarative, modern UI.

Architecture:

MVVM (Model-View-ViewModel): Enforces a clean separation of concerns between the UI layer, state management, and business logic.

Repository Pattern: Abstracting data sources, providing a clean API for data access to the ViewModels.

Asynchronous Programming: Kotlin Coroutines are used extensively for all asynchronous operations, including database queries and network calls, ensuring a responsive, non-blocking UI.

Backend as a Service (BaaS):

Firebase Authentication: Manages user registration, sign-in, sign-out, and password recovery.

Cloud Firestore: The primary cloud database for storing user profiles (users collection) and all activity logs (logs collection).

Local Database: Room for persisting location data collected in the background by WorkManager.

Background Processing:

WorkManager: For scheduling deferrable and guaranteed background tasks (location fetching).

AlarmManager: For scheduling exact, time-critical alarms (daily reminders).

Networking: Retrofit for type-safe HTTP requests to communicate with third-party APIs like the OpenWeatherApi.

Navigation: Jetpack Navigation for Compose (Implicit).