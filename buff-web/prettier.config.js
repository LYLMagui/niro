// https://prettier.io/docs/en/configuration.html

/** @type {import("prettier").Config} */
export default {
  // 每行字符数
  printWidth: 120,
  // 缩进空格数
  tabWidth: 2,
  // 使用空格缩进
  useTabs: false,
  // 语句末尾使用分号
  semi: true,
  // 使用单引号
  singleQuote: false,
  // 对象属性引号
  quoteProps: "as-needed",
  // JSX中使用双引号
  jsxSingleQuote: false,
  // 末尾逗号
  trailingComma: "es5",
  // 对象大括号空格
  bracketSpacing: true,
  // JSX括号换行
  bracketSameLine: false,
  // 箭头函数参数括号
  arrowParens: "always",
  // 文件末尾换行
  endOfLine: "lf",
  // 格式化嵌入代码
  embeddedLanguageFormatting: "auto",
  // HTML空白处理
  htmlWhitespaceSensitivity: "css",
  // 插入 @format 标记
  insertPragma: false,
  // 需要 @format 标记才格式化
  requirePragma: false,
  //  proseWrap: "preserve",
  // 使用插件
  plugins: ["prettier-plugin-tailwindcss"],
};
