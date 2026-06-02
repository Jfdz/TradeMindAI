import path from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  // Vitest 4's Rolldown transform ignores the old `esbuild.jsx` option, so JSX
  // in .tsx test files must be transformed by the React plugin.
  plugins: [react()],
  test: {
    environment: "node",
    globals: true,
    clearMocks: true,
    mockReset: true,
    exclude: ["**/node_modules/**", "**/tests/e2e/**"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname),
    },
  },
});
