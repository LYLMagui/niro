module.exports = {
  extends: [
    "stylelint-config-recommended",
    "stylelint-config-html",
    "stylelint-config-recommended-scss",
    "stylelint-config-recommended-vue",
    "stylelint-config-recess-order",
    "stylelint-prettier/recommended"
  ],
  overrides: [
    {
      files: ["*.vue", "**/*.vue", "*.html", "**/*.html"],
      customSyntax: "postcss-html"
    }
  ],
  rules: {
    "prettier/prettier": true,
    "no-empty-source": null,
    "selector-pseudo-class-no-unknown": [
      true,
      {
        ignorePseudoClasses: ["deep", "global"]
      }
    ],
    "no-descending-specificity": null,
    "at-rule-no-unknown": [
      true,
      {
        ignoreAtRules: ["tailwind", "apply", "variants", "responsive", "screen", "layer"]
      }
    ]
  }
};
