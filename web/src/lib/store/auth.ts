import { writable } from 'svelte/store';
import type { User as FirebaseUser } from 'firebase/auth';

export const currentUser = writable<FirebaseUser | null>(null);
export const authLoading = writable(true);
