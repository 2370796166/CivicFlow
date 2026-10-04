import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  { files: ['e2e/**/*.mjs'], languageOptions: { globals: { process: 'readonly', console: 'readonly' } } },
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  { files: ['**/*.vue'], languageOptions: { parserOptions: { parser: tseslint.parser, extraFileExtensions: ['.vue'] }, globals: { document: 'readonly', crypto: 'readonly', setTimeout: 'readonly', clearTimeout: 'readonly', setInterval: 'readonly', clearInterval: 'readonly' } } },
  { rules: { 'vue/multi-word-component-names': 'off' } },
]
