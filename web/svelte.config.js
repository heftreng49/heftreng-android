import { vitePreprocess } from "@sveltejs/vite-plugin-svelte";
import adapter from "@sveltejs/adapter-static";

const config = {
  preprocess: vitePreprocess(),
  compilerOptions: {
    // A11y uyarılarını build hatası değil, warning olarak tut
    // Faz 2'de post/[id] ve profile/[uid] refactor edilince temiz olacak
    warningFilter: (w) => !w.code.startsWith('a11y')
  },
  kit: {
    adapter: adapter({
      pages: "build",
      assets: "build",
      fallback: "index.html",   // SPA fallback — tüm rotalar index.html'e düşer
      precompress: false
    })
  }
};

export default config;
