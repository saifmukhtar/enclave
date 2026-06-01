-- Migration 10: Create restricted signaling_server role and grant least privilege permissions

-- 1. Create the custom database role if not exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'signaling_server') THEN
        CREATE ROLE signaling_server NOINHERIT;
    END IF;
END
$$;

-- 2. Grant role transition to the PostgREST authenticator user
GRANT signaling_server TO authenticator;

-- 3. Grant schema usage permissions
GRANT USAGE ON SCHEMA public TO signaling_server;

-- 4. Grant limited table/column select/update permissions
-- Signaling server only needs to query profiles table for push tokens and update online status
GRANT SELECT (id, push_token, fcm_token) ON public.profiles TO signaling_server;
GRANT UPDATE (is_online, last_seen) ON public.profiles TO signaling_server;

-- 5. Define RLS Policies for the signaling_server role
CREATE POLICY "Signaling server can read push tokens"
    ON public.profiles FOR SELECT
    TO signaling_server
    USING (true);

CREATE POLICY "Signaling server can update online status"
    ON public.profiles FOR UPDATE
    TO signaling_server
    USING (true)
    WITH CHECK (true);
