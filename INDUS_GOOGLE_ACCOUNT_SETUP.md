# ChexoScan account service setup (required before testing release)

ChexoScan uses **Google OAuth to prove identity**, **Supabase Postgres to store only the shop name and email**, and **Room on the phone for all inventory and sales**. **Supabase Auth is not used.** Local optional shop passwords are PBKDF2-hashed in private device preferences only.

This branch's secure account flow **will not work until the following setup is completed**. Do not upload a release APK yet.

1. Back up your Supabase `public.users` table (especially if existing accounts are present), then run `supabase/migrations/20261023_secure_google_accounts.sql` in Supabase SQL Editor. This removes OLD remotely stored password hashes and TOTP secrets. It also blocks direct API access to account records.
2. In Google Cloud Console create/identify the **Web application OAuth client ID** belonging to the same Google project as the Android OAuth client for package `com.chexo.scan`. Keep the Android client registered with the **release keystore SHA-1** when you sign the APK.
3. Add `GOOGLE_WEB_CLIENT_ID=<your web OAuth client ID>` to your untracked Android Studio project `local.properties`. It is used at compile time and does not contain a client secret. The Google client ID must match the server configuration.
4. Set the exact same value as a Supabase Edge Function secret: `supabase secrets set GOOGLE_WEB_CLIENT_ID=<your web OAuth client ID>`.
5. Deploy `chexoscan-account` using Supabase CLI, e.g. `supabase functions deploy chexoscan-account --no-verify-jwt`. The function explicitly verifies Google ID tokens against Google's JWKS, issuer, expiry and client ID before accessing the table. A Supabase **service-role key stays server-side** and must never appear in the Android app.
6. On a real device, test new Google signup, Google login with an existing account, incorrect local password, local password reset, stay-logged-in off/on, logout, and Google-authorized delete account. Check Supabase to confirm remote deletion.
7. Run a clean Android Studio release build, check barcode scanning, billing, receipt sizes, backup/restore, and verify Indus Appstore's listing and privacy policy before uploading.

**Migration limitation:** old accounts' remote SHA-256 passwords cannot be converted to secure local passwords without users re-entering them. Existing users can continue with Google Sign-In, then set a new local password. Existing phone inventory and sales are not migrated to the cloud.

**Reminder:** Cloud inventory syncing is intentionally disabled. Manual JSON export/import remains the only portable backup method.
