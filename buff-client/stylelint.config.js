// https://stylelint.io/user-guide/configure

/** @type {import("stylelint").Config} */
export default {
  // 继承的规则
  extends: [
    "stylelint-config-recommended", // 基础推荐规则
    "stylelint-config-recommended-vue", // Vue推荐规则
    "stylelint-config-recommended-scss", // SCSS推荐规则
    "stylelint-config-html", // HTML推荐规则
    "stylelint-config-recess-order", // 属性排序规则
  ],
  // 插件
  plugins: ["stylelint-prettier"],
  // 自定义规则
  rules: {
    // 与Prettier兼容
    "prettier/prettier": true,
    // 允许未知的伪类选择器
    "selector-pseudo-class-no-unknown": [
      true,
      {
        ignorePseudoClasses: ["deep", "global"],
      },
    ],
    // 允许未知的伪元素选择器
    "selector-pseudo-element-no-unknown": [
      true,
      {
        ignorePseudoElements: ["v-deep", "v-global", "v-slotted"],
      },
    ],
    // 允许未知的at规则
    "at-rule-no-unknown": [
      true,
      {
        ignoreAtRules: ["tailwind", "apply", "variants", "responsive", "screen", "layer"],
      },
    ],
    // 允许空的样式块
    "block-no-empty": null,
    // 允许未知的单位
    "unit-no-unknown": [
      true,
      {
        ignoreUnits: ["rpx"],
      },
    ],
    // 允许重复的属性
    "declaration-block-no-duplicate-properties": null,
    // 禁止低优先级的选择器出现在高优先级选择器之后
    "no-descending-specificity": null,
  },
  // 忽略的文件
  ignoreFiles: ["dist/**", "node_modules/**", "public/**"],
};
