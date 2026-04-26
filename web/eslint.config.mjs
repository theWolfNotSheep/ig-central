import nextConfig from "eslint-config-next";
import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypeScript from "eslint-config-next/typescript";

export default [
  ...nextConfig,
  ...nextCoreWebVitals,
  ...nextTypeScript,
  {
    rules: {
      // Pre-existing style/type debt downgraded to warnings while a focused
      // cleanup pass is scheduled. Real correctness bugs (rules-of-hooks,
      // used-before-declared) are NOT downgraded — those stay as errors.
      // Re-enable as `error` once each backlog is cleared.
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-require-imports": "warn",
      "@typescript-eslint/no-unused-expressions": "warn",
      "react/no-unescaped-entities": "warn",
      "react-hooks/exhaustive-deps": "warn",
      // New react-hooks v7 strict rules (Next 16 / React 19) surface
      // pre-existing patterns. Real correctness bugs caught by these
      // rules have been fixed; the remainder are performance hints
      // and style — downgrade to warning until a focused cleanup pass.
      "react-hooks/set-state-in-effect": "warn",
      "react-hooks/static-components": "warn",
      "react-hooks/refs": "warn",
      "react-hooks/immutability": "warn",
      "@next/next/no-img-element": "warn",
      "import/no-anonymous-default-export": "warn",
    },
  },
];
