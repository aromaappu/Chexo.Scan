# Indus Appstore release readiness — 23 October 2026

Status: **NOT READY TO SUBMIT**. This branch contains targeted fixes only. No signed APK has been built, installed, or reviewed by Indus.

## Changes on this branch
- Replace Room destructive-migration fallback with fail-safe behavior (a schema migration is required for any future schema changes).
- Validate backups before restore; replace inventory and sales atomically to avoid duplication/partial restore.
- Use scoped-storage MediaStore to save JSON backups on Android 10 and newer.
- Separate PDF generation and print dialog from recording the sale.
- Validate requested stock and save inventory changes and sales as a single Room transaction; refuse overselling.
- Clear the shared billing cart upon account change.
- Validate new product quantities and prices.
- Clarify that in-app account deletion currently removes *local* data, not the online Supabase record.

## Release blockers NOT addressed
1. **Authentication design:** current GoogleSignIn obtains an email, then trusts client-side lookup. It does not establish a server-verified user session. Email/username/password data can be read or updated from the client API depending on Supabase RLS policies. Protect user records with properly verified Google ID tokens on a secure backend and per-user RLS. Do not place a Supabase service-role credential inside an APK.
2. **Passwords:** current app reads user password hashes via PostgREST, using unsalted SHA-256 and client-side comparison. Prefer server-side authentication with a properly salted password hashing algorithm, or remove the manual password login entirely after a carefully planned migration that preserves existing users.
3. **Remote account erasure:** local Delete Account does not delete the online users row. Implement server-authorized deletion and update BOTH the in-app and hosted privacy policies before claiming that all data is erased.
4. **Signup / Google OAuth:** build a signed release APK and verify Google OAuth SHA-1, client ID, consent, and login/signup flows using the *release signing certificate*; private development builds are not sufficient.
5. **Backups:** test restoring across updates, malformed files, app reinstall, and low storage. Prevent oversized restores and offer a clear recovery path.
6. **Printing:** print dialog opening does not imply successful printing; test cancellation, print failures, page overflow, and 58/80 mm printers.
7. **Session setting:** Stay Logged In remains inconsistent with actual Supabase session restoration and the custom username/password workflow.
8. **User data:** audit Supabase RLS, data retention, and privacy claims regarding cloud product sync. Current app's Room inventory is local, while README/hosted policy claims cloud sync.
9. **Older Android:** PDF opens via file:// for Android 7–9; use FileProvider before relying on export/preview there.
10. **Versioning:** app/build.gradle.kts still uses versionCode 1. Confirm previously used Indus version code before incrementing. Build and verify store-signed release before submission.
11. **Production testing:** Gradle compile/tests, device smoke tests, crashes, accessibility, privacy URLs, and Indus submission checks remain unverified.

## Recommended end-to-end checks
- Clean install; Google account picker login; signup for new Google user; login/logout/restart; account switching; password recovery; denial of camera permission.
- Add/edit/delete stock; scan duplicates; sell last item; attempt oversell; repeat checkout; export PDF; cancel print; check sales reports.
- Back up and restore after having existing sales; import malformed JSON; restart; upgrade from existing app database; delete local data; request online erasure.
- Confirm the exact Indus portal requirements and privacy disclosures using the final build and its requested permissions.

The fixes in this branch do not guarantee Indus Appstore approval.
