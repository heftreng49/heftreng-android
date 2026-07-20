import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import adapter from '@sveltejs/adapter-cloudflare';

export default defineConfig({
  plugins: [sveltekit()],
  kit: {
    adapter: adapter()
  }
} as any);
