"use client";

import { useEffect } from "react";

import { useThemeStore } from "@/lib/theme-store";

export function ThemeHydrator() {
  const theme = useThemeStore((state) => state.theme);
  const hydrated = useThemeStore((state) => state.hydrated);
  const setTheme = useThemeStore((state) => state.setTheme);
  const setHydrated = useThemeStore((state) => state.setHydrated);

  useEffect(() => {
    const storedTheme = window.localStorage.getItem("tradermind-theme");

    if (storedTheme === "light" || storedTheme === "dark") {
      setTheme(storedTheme);
    }

    setHydrated(true);
  }, [setHydrated, setTheme]);

  useEffect(() => {
    if (!hydrated) {
      return;
    }

    window.localStorage.setItem("tradermind-theme", theme);
    document.documentElement.classList.toggle("dark", theme === "dark");
  }, [hydrated, theme]);

  return null;
}
