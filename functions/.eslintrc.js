module.exports = {
  env: {
    es6: true,
    node: true,
  },
  parserOptions: {
    "ecmaVersion": 2022,
  },
  extends: [
    "eslint:recommended",
    "google",
  ],
  rules: {
    "no-restricted-globals": ["error", "name", "length"],
    "linebreak-style": "off",
    "max-len": "off",
    "require-jsdoc": "off",
    "valid-jsdoc": "off",
    "quotes": "off",
    "comma-dangle": "off",
    "object-curly-spacing": "off",
    "indent": "off",
    "no-trailing-spaces": "off",
    "padded-blocks": "off",
    "arrow-parens": "off",
    "one-var": "off",
  },
  overrides: [
    {
      files: ["**/*.spec.*"],
      env: {
        mocha: true,
      },
      rules: {},
    },
  ],
  globals: {},
};
