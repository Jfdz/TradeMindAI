import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

const config: Config = {
  darkMode: "class",
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        bg: {
          0: "#07090e",
          1: "#0c1018",
          2: "#111720",
          3: "#182030",
        },
        border: {
          DEFAULT: "rgba(255,255,255,0.06)",
          strong: "rgba(255,255,255,0.10)",
        },
        cyan: {
          DEFAULT: "#00c8d4",
          dim: "rgba(0,200,212,0.15)",
          glow: "rgba(0,200,212,0.35)",
        },
        gold: {
          DEFAULT: "#e8b84b",
          dim: "rgba(232,184,75,0.12)",
        },
        green: "#00d68f",
        red: "#ff4d6a",
        text: {
          1: "#dce8f0",
          2: "#7a90a8",
          3: "#3d5268",
        },
        borderLegacy: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        ink: {
          950: "#08121f",
          900: "#0d1728",
          800: "#142238",
        },
        goldScale: {
          300: "#f6d08a",
          400: "#f0b95a",
        },
        mint: {
          300: "#86e7c8",
          400: "#54d5b4",
        },
      },
      boxShadow: {
        glow: "0 0 0 1px rgba(0, 200, 212, 0.12), 0 0 40px rgba(0, 200, 212, 0.10)",
        goldGlow: "0 0 0 1px rgba(232, 184, 75, 0.18), 0 0 40px rgba(232, 184, 75, 0.10)",
      },
      fontFamily: {
        sans: ["var(--font-inter)", "Inter", "sans-serif"],
        display: ["var(--font-space-grotesk)", "Space Grotesk", "sans-serif"],
        mono: ["var(--font-ibm-plex-mono)", "IBM Plex Mono", "monospace"],
      },
      keyframes: {
        marquee: {
          "0%": { transform: "translateX(0)" },
          "100%": { transform: "translateX(-50%)" },
        },
        pulseSoft: {
          "0%, 100%": { opacity: "0.4" },
          "50%": { opacity: "1" },
        },
        floatY: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-6px)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "0% 50%" },
          "100%": { backgroundPosition: "200% 50%" },
        },
      },
      animation: {
        marquee: "marquee 30s linear infinite",
        "pulse-soft": "pulseSoft 2s ease-in-out infinite",
        float: "floatY 8s ease-in-out infinite",
        shimmer: "shimmer 8s linear infinite",
      },
    },
  },
  plugins: [tailwindcssAnimate],
};

export default config;
