import { createClient } from '@supabase/supabase-js';
import { browser } from '$app/environment';

const supabaseUrl  = import.meta.env.VITE_SUPABASE_URL;
const supabaseKey  = import.meta.env.VITE_SUPABASE_ANON_KEY;

export const supabase = browser
  ? createClient(supabaseUrl, supabaseKey)
  : (null as any);
