# ZBK Credit Companion (mobile)

Kotlin Multiplatform Android MVP — see [ADR 0002](../docs/mobile/0002-mobile-kmp-architecture.md).

## Modules

| Module | Role |
| --- | --- |
| `shared` | Ktor client, Koin DI, DTOs, `BankingRepository` |
| `androidApp` | Native Jetpack Compose UI |

## Local API base URL

Emulator → host Vite/Nginx proxy:

`http://10.0.2.2:5173/api/v1`

Start the stack (`docker compose up` or Vite + services), then run `androidApp` from Android Studio
(open the `mobile/` folder as a Gradle project).

## First sync

Open `mobile/` in Android Studio (Ladybug+). Gradle Wrapper is generated on first import if missing;
or from this directory:

```powershell
gradle wrapper --gradle-version 8.11.1
.\gradlew.bat :androidApp:assembleDebug
```
