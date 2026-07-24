// @ts-check
// CRM Lite frontend ESLint flat config.
// Enforces FE-ADR-003 layer boundaries mechanically (no extra plugin — built-in
// no-restricted-imports, per FE-ADR-003 §Consequences) plus the quality rules
// required by the task: no implicit/explicit any, no unused symbols, no stray
// console.log (console.warn/error stay allowed — FE-ADR-008 §3 uses them).
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

/**
 * FE-ADR-003 import graph is one-directional:
 *   features -> core | shared      core -> shared      shared -> (nothing)
 * Patterns match the import SPECIFIER string, so both relative
 * (`../../features/...`) and any future path-alias (`@features/...`) forms are
 * caught. Sibling-feature imports are a review concern per FE-ADR-003
 * §Consequences (built-in rules enforce only the two directions below).
 */
const FORBID_FEATURES = {
  group: ['**/features/*', '**/features/**', '@features/*', '@features/**'],
  message:
    'FE-ADR-003: core/ and shared/ must not import from features/. Move the shared piece into shared/ or invert the dependency.',
};
const FORBID_CORE = {
  group: ['**/core/*', '**/core/**', '@core/*', '@core/**'],
  message:
    'FE-ADR-003: shared/ must not import from core/. shared/ stays feature- and app-agnostic.',
};

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      // Quality gates (task requirement).
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'no-console': ['error', { allow: ['warn', 'error'] }],
    },
  },

  // FE-ADR-003: core/ may not reach into features/.
  {
    files: ['src/app/core/**/*.ts'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [FORBID_FEATURES] }],
    },
  },

  // FE-ADR-003: shared/ may not reach into core/ or features/.
  {
    files: ['src/app/shared/**/*.ts'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [FORBID_CORE, FORBID_FEATURES] }],
    },
  },

  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
