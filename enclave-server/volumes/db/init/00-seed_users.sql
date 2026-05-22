-- Ensure auth.users has the is_anonymous column for newer Supabase Studio/Auth compatibility
ALTER TABLE auth.users ADD COLUMN IF NOT EXISTS is_anonymous boolean NOT NULL DEFAULT false;

-- Seed mock users into auth.users to satisfy foreign key constraints
INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, role, aud, confirmation_token, recovery_token, email_change_token_new, email_change)
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'me@enclave.local', '$2a$12$mzrExJqeIdpFhIYR0ZNHGO/V4NfQM3r2Yp7R0YRodSpuKVjDalDDW', now(), '{}'::jsonb, '{}'::jsonb, now(), now(), 'authenticated', 'authenticated', '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', 'partner@enclave.local', '$2a$12$mzrExJqeIdpFhIYR0ZNHGO/V4NfQM3r2Yp7R0YRodSpuKVjDalDDW', now(), '{}'::jsonb, '{}'::jsonb, now(), now(), 'authenticated', 'authenticated', '', '', '', '')
ON CONFLICT (id) DO NOTHING;
