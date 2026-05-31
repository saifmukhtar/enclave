-- Ensure auth.users has the is_anonymous column for newer Supabase Studio/Auth compatibility
ALTER TABLE auth.users ADD COLUMN IF NOT EXISTS is_anonymous boolean NOT NULL DEFAULT false;
