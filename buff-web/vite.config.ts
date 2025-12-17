import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { TDesignResolver } from 'unplugin-vue-components/resolvers';
import tailwindcss from '@tailwindcss/vite';
import Icons from 'unplugin-icons/vite';
import IconsResolver from 'unplugin-icons/resolver';
import vueDevTools from 'vite-plugin-vue-devtools';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // Tailwind CSS v4 Plugin
    tailwindcss(),
    // Auto Import APIs
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
      resolvers: [
        TDesignResolver({
          library: 'vue-next',
        }),
      ],
      dts: 'src/auto-imports.d.ts',
      eslintrc: {
        enabled: true, // Generate .eslintrc-auto-import.json
      },
    }),
    // Auto Import Components
    Components({
      resolvers: [
        TDesignResolver({
          library: 'vue-next',
        }),
        IconsResolver({
          prefix: 'icon', // usage: <icon-mdi-account />
        }),
      ],
      dts: 'src/components.d.ts',
    }),
    // Auto Import Icons
    Icons({
      autoInstall: true,
      compiler: 'vue3',
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
});
