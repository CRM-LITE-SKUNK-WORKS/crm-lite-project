/** Public surface of core/auth. */
export { AuthService } from './auth.service';
export { authGuard, crmUserGuard } from './auth.guard';
export { provideSessionRestoreCheck } from './session-restore';
export type { SessionResponse } from './session.model';
