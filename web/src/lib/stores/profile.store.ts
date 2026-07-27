// Android ProfileViewModel state karşılığı
import { writable } from 'svelte/store';
import type { User } from '$lib/models/user';
import type { Post } from '$lib/models/post';

export const profileUser     = writable<User | null>(null);
export const profilePosts    = writable<Post[]>([]);
export const profileLoading  = writable(false);
export const isFollowing     = writable(false);
export const profileHasMore  = writable(true);
export const profileLastDoc  = writable<unknown>(null);
