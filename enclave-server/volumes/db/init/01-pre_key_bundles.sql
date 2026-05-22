-- Create the pre_key_bundles table
CREATE TABLE IF NOT EXISTS public.pre_key_bundles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    identity_key TEXT NOT NULL,
    signed_pre_key JSONB NOT NULL,
    one_time_pre_keys JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS for pre_key_bundles
ALTER TABLE public.pre_key_bundles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own key bundle"
    ON public.pre_key_bundles
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own key bundle"
    ON public.pre_key_bundles
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Authenticated users can read all bundles"
    ON public.pre_key_bundles
    FOR SELECT
    USING (auth.role() = 'authenticated');

-- Create the profiles table for secure FCM push token delivery
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    push_token TEXT,
    fcm_token TEXT,
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS for profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own profile"
    ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update their own profile"
    ON public.profiles
    FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

CREATE POLICY "Authenticated users can read all profiles"
    ON public.profiles
    FOR SELECT
    USING (auth.role() = 'authenticated');

-- Trigger to update `updated_at` automatically
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_pre_key_bundles_modtime
    BEFORE UPDATE ON public.pre_key_bundles
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_profiles_modtime
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
