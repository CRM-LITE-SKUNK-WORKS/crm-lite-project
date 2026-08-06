/**
 * shared/patterns — composite patterns the EDS design system does NOT define;
 * rebuilt from the mock's hand-written markup (FE-ADR-011 §d, mock-ui-analysis
 * §7.2). Same layer contract as shared/ui (FE-ADR-003, linted): imports
 * NOTHING from core/ or features/ — all text arrives RESOLVED (`'KEY' | t` at
 * the call site, scope §4.14), all data and decisions come from the caller.
 */
export {
  Table,
  TableCellDef,
  TableExpansionDef,
  type TableCellContext,
  type TableColumn,
} from './table/table';
export { EmptyState } from './empty-state/empty-state';
export { Skeleton } from './skeleton/skeleton';
export { Pagination } from './pagination/pagination';
export {
  PAGE_SIZE_OPTIONS,
  DEFAULT_PAGE_SIZE,
  isPageSize,
  type PageSize,
} from './pagination/page-size';
export { Toast, type ToastKind } from './toast/toast';
export { Modal, ModalFooter, type ModalSize } from './modal/modal';
export { Tabs, type TabItem } from './tabs/tabs';
export { ConfirmDialog, type ConfirmTone } from './confirm-dialog/confirm-dialog';
export { StatusBadge, type StatusBadgeVariant } from './status-badge/status-badge';
export { Stepper, type StepItem, type StepperVariant } from './stepper/stepper';
