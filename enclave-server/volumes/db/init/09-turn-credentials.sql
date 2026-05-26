-- Enclave TURN Server Credentials Schema
-- Run this in your Supabase SQL Editor

CREATE TABLE IF NOT EXISTS public.turn_credentials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_url TEXT NOT NULL,
    turn_username TEXT NOT NULL,
    turn_password TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Turn on Row Level Security
ALTER TABLE public.turn_credentials ENABLE ROW LEVEL SECURITY;

-- Allow authenticated users to READ ONLY
CREATE POLICY "Allow authenticated users to read TURN credentials"
ON public.turn_credentials
FOR SELECT
TO authenticated
USING (true);

-- Insert your credentials below (Replace with your actual Coturn values)
INSERT INTO public.turn_credentials (turn_url, turn_username, turn_password)
VALUES (
    'turn:your-turn-server.com:3478', 
    'your-username', 
    'your-password'
);
