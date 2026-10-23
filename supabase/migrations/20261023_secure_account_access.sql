-- Apply using the Supabase SQL editor AFTER backing up public.users.
-- These statements prevent anonymous access to account information.
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.users FROM anon, authenticated;

-- Ensure the users table only contains email + username for account
-- identification. Remove obsolete password fields only after reviewing
-- existing users and your own backup.
-- The account Edge Function accesses this table with a server-side service key.
