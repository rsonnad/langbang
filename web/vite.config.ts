import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteSingleFile } from "vite-plugin-singlefile";

// Single-file build: the entire app inlines into one dist/index.html so it can be
// embedded into the langbang.org site Worker (served at /app) with zero separate
// asset hosting. base "./" keeps any stray URLs relative regardless of mount path.
export default defineConfig({
  base: "./",
  plugins: [react(), viteSingleFile()],
  build: {
    target: "es2020",
    cssCodeSplit: false,
    assetsInlineLimit: 100_000_000,
    chunkSizeWarningLimit: 4000,
  },
});
