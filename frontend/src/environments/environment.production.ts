/**
 * Production replacement for `environment.ts`
 * (angular.json -> build > configurations > production > fileReplacements).
 *
 * Same shape as the base environment. Per FE-ADR-004 §2 the only permitted
 * difference is a build/optimization discriminator — NO API host lives here
 * either; production still issues relative paths, forwarded by nginx.
 */
export const environment = {
  production: true,
} as const;
