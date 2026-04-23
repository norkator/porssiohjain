import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd());
const distDir = resolve(root, "dist");
const targetDir = resolve(root, "../../energy-controller-android/app/src/main/assets/hybrid");

if (!existsSync(distDir)) {
  console.error("Missing dist/ directory. Build the web project first.");
  process.exit(1);
}

rmSync(targetDir, { recursive: true, force: true });
mkdirSync(targetDir, { recursive: true });
cpSync(distDir, targetDir, { recursive: true });

console.log(`Copied ${distDir} -> ${targetDir}`);
