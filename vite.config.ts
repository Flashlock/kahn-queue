import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Project Pages URL: https://<org>.github.io/kahn-queue/
export default defineConfig({
  plugins: [react()],
  base: "/kahn-queue/",
});
