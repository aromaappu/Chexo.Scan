-- Apply after exporting a secure backup of existing account rows.
-- Password authentication is replaced with Google OAuth verification.
-- Existing rows are preserved; new registrations no longer need a password.
ALTER TABLE public.users ALTER COLUMN password DROP NOT NULL;
