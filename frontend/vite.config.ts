import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: { environment: "jsdom", setupFiles: ["./src/test/setup.ts"] },
  server: {
    host: "127.0.0.1",
    proxy: {
      "/api": process.env.BACKEND_URL || "http://127.0.0.1:8080",
      "/actuator": process.env.BACKEND_URL || "http://127.0.0.1:8080",
    },
  },
});
