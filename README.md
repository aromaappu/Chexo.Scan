<div align="center">

# 📷 ChexoScan

**Android barcode-scanning & billing app for small shops — scan, sell, and bill in seconds**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Supabase](https://img.shields.io/badge/Supabase-Backend-3FCF8E?style=for-the-badge&logo=supabase&logoColor=white)](https://supabase.com/)
[![Android](https://img.shields.io/badge/API_24+-Android_7.0-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)

*ML Kit Barcode Scanning · Cloud Sync · Bill Generation · Offline-first Room Cache*

</div>

---

## 📖 About The Project

ChexoScan turns an ordinary Android phone into a shop counter tool. Shop owners scan
packed-product barcodes with the camera, look up prices instantly from the cloud catalog,
and generate customer bills — no dedicated POS hardware required.

### 🎯 Key Highlights

- 📷 **Real-time scanning** — camera preview + ML Kit analyzer decodes barcodes on-device
- ☁️ **Supabase backend** — products, sales and user profiles sync to PostgreSQL
- 🧾 **Bill generation** — multi-item carts become shareable bills in two taps
- 🔐 **Full auth flow** — email sign-up / login with Supabase Auth + deep-link session handling
- 🎨 **100% Jetpack Compose** — modern declarative UI, no XML layouts

---

## ✨ Features

| | |
|---|---|
| 🔄 **Login / Sign-up** | Animated rotating-circle UI, persistent sessions, back-button aware navigation |
| 📷 **Barcode Scanning** | Live CameraX preview with ML Kit barcode analysis; scan-to-add and scan-to-lookup modes |
| 🏷️ **Product Catalog** | Cloud product list with instant client-side filtering |
| 🛒 **Billing** | Multi-item cart, totals calculation, bill generation & sharing |
| 💾 **Sales History** | Every sale persisted via Room DAO for offline resilience |
| ⚙️ **Settings** | In-app configuration screen backed by DataStore (`SettingsData`) |

---

## 🏗️ Architecture

```
UI (Jetpack Compose screens)
   │  ViewModel state
   ▼
Data layer
   ├── SupabaseClient ──► Supabase (Auth + PostgREST)   ← cloud sync
   └── Room (SaleDao)  ──► local SQLite cache           ← offline history
```

```
app/src/main/java/com/chexo/scan/
├── MainActivity.kt            # Nav host, auth deep-links, back handling
├── data/
│   └── SaleDao.kt             # Room persistence for sales
├── ui/
│   ├── LoginScreen.kt         # rotating-circle auth animation
│   ├── HomeScreen.kt          # scanner hub + product list + billing
│   ├── SettingsScreen.kt
│   └── BarcodeScannerView.kt  # CameraX + ML Kit integration
└── SettingsData.kt            # DataStore preferences
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Camera | CameraX + ML Kit Barcode Scanning |
| Backend | Supabase (PostgreSQL, PostgREST, Auth) — `supabase-kt` |
| Local storage | Room + DataStore |
| Min / Target SDK | 24 (Android 7.0) / 37 |

---

## 🚀 Getting Started

1. Clone and open in **Android Studio**
2. Let Gradle sync
3. Add your Supabase credentials to `local.properties`:

   ```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```

4. Create the tables (see SQL below) and run on a device/emulator

<details>
<summary><b>Database schema</b></summary>

```sql
-- Products
CREATE TABLE products (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  barcode TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Bills
CREATE TABLE bills (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id),
  items JSONB NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);
```

</details>

---

## 📸 Screenshots

> Add screenshots here before sharing: login animation, scanner view, product list, generated bill.

---

## 👤 Author

**Appu Aromal** — [GitHub](https://github.com/aromaappu)

---

<div align="center">
<sub>Kotlin · Compose · CameraX · ML Kit · Supabase</sub>
</div>
