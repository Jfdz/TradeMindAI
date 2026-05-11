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
          bright: "#22e6ef",
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
        buy: {
          DEFAULT: "#10b981",
          ring: "#6ee7b7",
        },
        sell: {
          DEFAULT: "#f43f5e",
          ring: "#fda4af",
        },
        hold: {
          DEFAULT: "#f59e0b",
          ring: "#fcd34d",
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
        neon: "0 0 12px rgba(0,200,212,0.45), 0 0 32px rgba(0,200,212,0.18)",
        "neon-soft": "0 0 6px rgba(0,200,212,0.25), 0 0 16px rgba(0,200,212,0.10)",
        "buy-glow": "0 0 24px rgba(16,185,129,0.35)",
        "sell-glow": "0 0 24px rgba(244,63,94,0.35)",
        "hold-glow": "0 0 24px rgba(245,158,11,0.35)",
      },
      backgroundImage: {
        "gradient-hero": "radial-gradient(ellipse at 30% 0%, rgba(0,200,212,0.12) 0%, transparent 65%)",
        "buy-gradient": "linear-gradient(to right, #10b981, #34d399)",
        "sell-gradient": "linear-gradient(to right, #f43f5e, #fb7185)",
        "hold-gradient": "linear-gradient(to right, #f59e0b, #fcd34d)",
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
