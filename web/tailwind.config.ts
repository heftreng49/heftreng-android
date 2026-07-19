import type { Config } from "tailwindcss"

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        // Tema renkleri CSS değişkenlerinden gelir
        bg:          "var(--bg)",
        surface:     "var(--surface)",
        "surface-v": "var(--surface-var)",
        card:        "var(--card)",
        "on-bg":     "var(--on-bg)",
        "on-surface":"var(--on-surface)",
        primary:     "var(--primary)",
        "primary-l": "var(--primary-light)",
        accent:      "var(--accent)",
        muted:       "var(--muted)",
        divider:     "var(--divider)",
        amber:       "#F59E0B",
        success:     "#34D399",
        error:       "#F87171",
      },
      fontFamily: {
        sans:    ["Inter", "system-ui", "sans-serif"],
        display: ["Playfair Display", "Georgia", "serif"],
      },
      borderRadius: {
        DEFAULT: "12px",
        sm: "8px",
        lg: "16px",
        xl: "20px",
      },
      transitionDuration: {
        DEFAULT: "200ms",
      },
      backdropBlur: {
        nav: "20px",
      },
    },
  },
  plugins: [],
}

export default config
