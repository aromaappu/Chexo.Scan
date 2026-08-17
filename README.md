# ChexoScan

A barcode scanning and bill generation app for small shops built with Kotlin and Jetpack Compose.

## Features

- **Login / Sign Up** - Rotating circle UI with email and password authentication
- **Barcode Scanning** - Scan packed product barcodes and save to database
- **Product Lookup** - Scan barcodes to view product details
- **Bill Generation** - Add multiple products and generate bills
- **Supabase Integration** - Cloud database for storing products and user data

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Database:** Supabase (PostgreSQL)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 37

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Add your Supabase URL and API key to `local.properties`
5. Run on device or emulator

## Project Structure

```
app/src/main/java/com/chexo/scan/
├── MainActivity.kt          # Entry point
├── ui/
│   ├── LoginScreen.kt       # Login/Signup with rotating circle
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
```

## Supabase Setup

Create a Supabase project and add the following tables:

```sql
-- Users table (handled by Supabase Auth)

-- Products table
CREATE TABLE products (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  barcode TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Bills table
CREATE TABLE bills (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id),
  total DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Bill items table
CREATE TABLE bill_items (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  bill_id UUID REFERENCES bills(id),
  product_id UUID REFERENCES products(id),
  quantity INT NOT NULL,
  price DECIMAL(10,2) NOT NULL
);
```

## License

MIT
