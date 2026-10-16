import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],

  server: {
    port: 5174,
    strictPort: true,

    proxy: {
      "/api": "http://127.0.0.1:8083",
      "/oauth2": "http://127.0.0.1:8083",
      "/login": "http://127.0.0.1:8083",
      "/actuator": "http://127.0.0.1:8083",
    },
  },

  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.js"],
  },
});