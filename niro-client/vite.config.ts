import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import vueDevTools from "vite-plugin-vue-devtools";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import tailwindcss from "@tailwindcss/vite";
import { TDesignResolver } from "unplugin-vue-components/resolvers";
import Icons from "unplugin-icons/vite";
import IconsResolver from "unplugin-icons/resolver";
import { resolve } from "path";

// https://vite.dev/config/

/**
 * 根据环境的不同动态切换路径
 */
export default defineConfig(({ mode }) => {
  /**
   * loadEnv 接收三个参数：
   * mode：模式
   * envDir：环境变量配置文件所在目录
   * prefix：接受的环境变量前缀，默认为 VITE_
   */
  const env = loadEnv(mode, process.cwd());

  return {
    base: env.VITE_BASE || "/",
    server: {
      open: true, // 启动项目时是否打开页面
      host: "0.0.0.0",
      port: +env.VITE_PORT || 5173,
      proxy: {
        // 代理 /dev-api 的请求，这里使用了动态键值对
        [env.VITE_BASE_API || "/api"]: {
          // 从环境变量中获取代理地址
          target: env.VITE_APP_URL || "http://localhost:8080",
          //允许跨域
          changeOrigin: true,
          rewrite: (path) => path.replace(new RegExp("^" + (env.VITE_BASE_API || "/api")), ""),
        },
      },
      warmup: {
        clientFiles: ["./src/main.ts", "./src/views/**/*.vue"],
      },
    },
    optimizeDeps: {
      include: [
        "vue",
        "vue-router",
        "pinia",
        "axios",
        "lodash-es",
        "tdesign-vue-next",
        "tdesign-icons-vue-next",
        "@vueuse/core",
        "qs",
      ],
    },
    plugins: [
      vue(),
      vueDevTools(),
      // 自动导入插件
      AutoImport({
        // 需要自动导入的模块
        imports: ["vue", "@vueuse/core", "vue-router", "pinia"],
        eslintrc: {
          // 是否自动生成 eslint 规则，第一次为true，生成之后设置为false防止重复生成
          enabled: true,
          // 指定自动导入函数 eslint 规则的文件
          filepath: "./.eslintrc-auto-import.json",
          globalsPropValue: true,
        },
        vueTemplate: true,
        // 指定自动导入函数TS类型声明文件路径
        dts: "src/types/auto-imports.d.ts",
        resolvers: [TDesignResolver({ library: "vue-next" })],
      }),
      // 按需自动导入组件
      Components({
        // 第三方组件库的自动导入，需要对应组件的配置
        resolvers: [
          TDesignResolver({ library: "vue-next" }),
          IconsResolver({
            prefix: false,
            enabledCollections: ["line-md"],
          }),
        ],
        // 想要自动导入的组件所在目录
        dirs: ["src/components", "src/**/components"],
        // 导入组件类型声明文件路径 (false:关闭自动生成)
        dts: "src/types/components.d.ts",
      }),
      // tailwindcss
      tailwindcss(),
      // 图标自动导入
      Icons({
        autoInstall: true,
        compiler: "vue3",
        scale: 1,
        defaultClass: "inline-block",
      }),
    ],
    resolve: {
      alias: {
        "@": resolve(__dirname, "./src"),
        "@component": resolve(__dirname, "./src/components"),
      },
      extensions: [".js", ".ts", ".json", ".vue", ".mjs"],
    },
    // 构建优化
    build: {
      // 启用压缩
      minify: "esbuild",
      // 分包策略
      rollupOptions: {
        output: {
          manualChunks: {
            "vue-vendor": ["vue", "vue-router", "pinia"],
            "ui-vendor": ["tdesign-vue-next"],
            "utils-vendor": ["@vueuse/core", "axios", "qs", "lodash-es"],
          },
        },
      },
    },
  };
});
