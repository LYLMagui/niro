// https://eslint.nodejs.cn/docs/latest/use/configure/configuration-files

import globals from "globals";
import pluginJs from "@eslint/js"; // JavaScript 规则
import pluginVue from "eslint-plugin-vue"; // Vue 规则
import pluginTypeScript from "@typescript-eslint/eslint-plugin"; // TypeScript 规则

import parserVue from "vue-eslint-parser"; // Vue 解析器
import parserTypeScript from "@typescript-eslint/parser"; // TypeScript 解析器

import configPrettier from "eslint-config-prettier"; // 与 Prettier 兼容
import pluginPrettier from "eslint-plugin-prettier"; // 运行 Prettier
import fs from "fs";

const autoImportConfig = JSON.parse(fs.readFileSync(".eslintrc-auto-import.json", "utf-8"));
// 自动导入函数

/** @type {import("eslint").Linter.Config[]} */
export default [
  // 全局忽略文件
  {
    files: ["**/*.{js,mjs,cjs,ts,vue}"],
    ignores: [
      "**/*.d.ts",
      "vite.config.ts",
      "dist/**",
      "node_modules/**",
      "public/**",
      ".husky/**",
      ".vscode/**",
      ".idea/**",
      "*.sh",
      "src/assets.eslintrc.cjs",
      "eslint.config.js",
      "stylelint.config.js",
      "*.md",
      "tsconfig.json",
    ],
  },
  // 运行 Prettier
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        ...autoImportConfig.globals,
        ...{
          PageQuery: "readonly",
          PageResult: "readonly",
          OptionType: "readonly",
          ResponseData: "readonly",
          ExcelResult: "readonly",
          TagView: "readonly",
          AppSettings: "readonly",
          __APP_INFO__: "readonly",
        },
      },
    },
    plugins: { prettier: pluginPrettier },
    rules: {
      ...configPrettier.rules, // 与 Prettier 兼容
      ...pluginPrettier.configs.recommended.rules, // 运行 Prettier
      "prettier/prettier": "error", // 强制 Prettier 格式化
      "no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_", // 忽略以 _ 开头的参数
          varsIgnorePattern: "^[A-Z0-9_]+$", // 忽略全大写的变量
          ignoreRestSiblings: true, // 忽略剩余属性
        },
      ],
    },
  },
  // JavaScript 规则
  pluginJs.configs.recommended,

  // TypeScript 规则
  {
    files: ["**/*.ts"],
    ignores: ["**/*.d.ts"], // 忽略 .d.ts 文件
    languageOptions: {
      parser: parserTypeScript,
      parserOptions: {
        sourceType: "module",
      },
    },
    plugins: { "@typescript-eslint": pluginTypeScript },
    rules: {
      ...pluginTypeScript.configs.strict.rules, // TypeScript 严格规则
      "@typescript-eslint/no-explicit-any": "off", // 允许使用 any
      "@typescript-eslint/no-empty-function": "off", // 允许空函数
      "@typescript-eslint/no-empty-object-type": "off", // 允许空对象类型
    },
  },

  // Vue 规则
  {
    files: ["**/*.vue"],
    languageOptions: {
      parser: parserVue,
      parserOptions: {
        parser: parserTypeScript,
        sourceType: "module",
      },
    },
    plugins: { vue: pluginVue, "@typescript-eslint": pluginTypeScript },
    rules: {
      ...pluginVue.configs.recommended.rules, // Vue 推荐规则
      "vue/no-v-html": "off", // 允许 v-html
      "vue/multi-word-component-names": "off", // 允许多单词组件名
    },
  },
];
