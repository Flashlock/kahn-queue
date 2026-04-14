import { defineConfig } from "tsup";

export default defineConfig({
  entry: ["src/index.ts"],

  format: ["esm", "cjs"],

  dts: true, // generate types
  sourcemap: true,
  clean: true,

  splitting: false, // important for libraries
  treeshake: true,

  target: "es2022",
});
