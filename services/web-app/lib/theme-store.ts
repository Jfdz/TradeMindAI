"use client";

import { create } from "zustand";

type Theme = "light" | "dark";

type ThemeState = {
  theme: Theme;
  hydrated: boolean;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
  setHydrated: (hydrated: boolean) => void;
};

export const useThemeStore = create<ThemeState>((set) => ({
  theme: "dark",
  hydrated: false,
  setTheme: (theme) => set({ theme }),
  toggleTheme: () =>
    set((state) => ({
      theme: state.theme === "dark" ? "light" : "dark",
    })),
  setHydrated: (hydrated) => set({ hydrated }),
}));
