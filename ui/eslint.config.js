import js from '@eslint/js'
import tseslint from 'typescript-eslint'

// Type-aware linting, matching the ripple library's setup. The UI is browser code, so TypeScript
// (with the DOM + vite libs in tsconfig) is what checks for undefined identifiers — ESLint's
// no-undef is redundant here and would false-flag browser globals, so it is turned off for .ts.
export default tseslint.config(
  { ignores: ['dist/', 'node_modules/'] },
  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  { files: ['**/*.ts'], rules: { 'no-undef': 'off' } },
  { files: ['**/*.js'], ...tseslint.configs.disableTypeChecked },
)
