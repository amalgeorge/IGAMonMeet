import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Output goes straight into the WAR's static resources (see pom.xml exec-maven-plugin).
export default defineConfig({
  // Relative asset paths so the WAR works at any context path (e.g. /igamonmeet/).
  base: './',
  plugins: [react()],
  build: { outDir: '../target/classes/static', emptyOutDir: true },
  server: { proxy: { '/api': 'http://localhost:8080' } }
});
