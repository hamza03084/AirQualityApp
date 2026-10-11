# Air Quality Monitor

An Android app that shows live air quality (AQI) for major cities in Pakistan, estimates AQI on the device with a TensorFlow Lite model, and alerts users when the air becomes unhealthy.

Built as my BS Computer Science final year project at Virtual University of Pakistan.

## Features

### For citizens

- **Live AQI** for eight cities (Lahore, Karachi, Islamabad, Rawalpindi, Faisalabad, Multan, Peshawar, Quetta), with PM2.5, PM10, temperature, humidity and wind speed.
- **Health guidance**: every reading is placed in one of six US EPA categories and shown with its health impact and a recommendation.
- **AI-predicted AQI** from an on-device TensorFlow Lite model. If the model cannot load, the app falls back to the EPA PM2.5 formula.
- **Manual input**: enter your own sensor readings and get a predicted AQI.
- **History and trends**: a trend chart and statistics for today, the past week, the past month or all readings.
- **Alerts**: a notification when the AQI crosses the alert threshold, limited to one per city and category per hour.
- **Offline support**: readings are stored on the device and backed up to the cloud when the connection returns.
- **Accounts**: email and password or Google sign-in, with a profile, a preferred city and notification settings.
- **Help and feedback**: an AQI guide, FAQs and an in-app feedback form.

### For admins

- **Dashboard** with user, dataset and feedback counts.
- **User management**.
- **AQI thresholds and health recommendations**: changes reach every signed-in device in real time.
- **Datasets**: import historical AQI data from CSV (including the Kaggle Pakistan Air Quality export) or add entries by hand.
- **Feedback inbox** and an **activity monitor** for manual submissions, alerts sent and new registrations.

## Tech stack

| Area | Tools |
| --- | --- |
| Language and UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| Local storage | Room, DataStore |
| Backend | Firebase Authentication, Cloud Firestore, Realtime Database, Cloud Messaging |
| Networking | Retrofit, Moshi, OkHttp |
| Data source | OpenWeatherMap (geocoding, air pollution and weather APIs) |
| Background work | WorkManager |
| Machine learning | TensorFlow Lite |
| Testing | JUnit 4 |

## How it works

1. The app turns the city name into coordinates, then fetches air pollution and weather data from OpenWeatherMap.
2. It calculates the AQI from PM2.5 using the US EPA breakpoints.
3. The TensorFlow Lite model estimates AQI from five inputs: PM2.5, PM10, temperature, humidity and wind speed. Inputs are standardized with the mean and standard deviation used in training.
4. The reading is saved in Room, published to Realtime Database, and later backed up to Firestore.
5. The reading is checked against the admin-configured alert threshold, and a notification is raised if it crosses it.

Two WorkManager jobs keep the data current, and both run only when the device is online:

| Job | Runs every | What it does |
| --- | --- | --- |
| `AqiFetchWorker` | 60 minutes | Fetches the AQI for the user's preferred city and checks the alert threshold |
| `SyncWorker` | 30 minutes | Uploads unsynced readings and manual submissions to Firestore |

## Project structure

```
app/src/main/java/com/example/airqualityapp/
├── data/local        Room database, DAOs and entities
├── data/remote       Retrofit APIs, Firebase helpers, messaging service
├── data/repository   AQI, user and admin repositories
├── data/worker       WorkManager jobs (AQI fetch and sync)
├── ml                TensorFlow Lite predictor
├── navigation        Navigation graph and routes
├── ui                Compose screens: auth, citizen, admin, help
└── util              AQI maths, classification, alerts, CSV parser
```

The model file is `app/src/main/assets/aqi_model.tflite`. Firebase security rules are in `firestore.rules` and `database.rules.json`.

## Run it locally

You need Android Studio with JDK 21 and Android SDK 36. The app runs on Android 7.0 (API 24) and above.

1. Clone the repository and open it in Android Studio.
2. Create a Firebase project and add an Android app with the package name `com.example.airqualityapp.new`. For Google sign-in, also register your debug SHA-1 fingerprint.
3. Enable **Authentication** (Email/Password and Google), **Cloud Firestore** and **Realtime Database**.
4. Download your own `google-services.json` and replace `app/google-services.json`.
5. Publish the security rules:
   ```bash
   firebase deploy --only firestore:rules,database
   ```
6. Create a free [OpenWeatherMap](https://openweathermap.org/api) API key and set it as `OPENWEATHER_KEY` in `app/src/main/java/com/example/airqualityapp/data/repository/AqiRepository.kt`.
7. Run the app from Android Studio, or build it from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

New accounts are created as citizens. To open the admin dashboard, set `role` to `admin` on that user's document in the Firestore `users` collection.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

The JVM unit tests cover the EPA AQI calculation, category classification, threshold validation, CSV dataset parsing and history trend aggregation.

## Author

Hamza Ashfaq ([@hamza03084](https://github.com/hamza03084))
