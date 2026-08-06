/**
 * shared/ui — the EDS component library (FE-ADR-011 §d).
 * Seven components; `PasswordInput` is deliberately absent (FE-ADR-005 §P5).
 * This layer imports NOTHING from core/ or features/ (FE-ADR-003, linted).
 */
export { Icon } from './icon/icon';
export { ICON_PATHS, type IconName, type IconSize } from './icon/icons';
export { Button, type ButtonSize, type ButtonVariant } from './button/button';
export {
  IconButton,
  type IconButtonSize,
  type IconButtonVariant,
} from './icon-button/icon-button';
export { FormField } from './form-field/form-field';
export { TextInput } from './text-input/text-input';
export { Select, type SelectOption, type SelectValue } from './select/select';
export { DatePicker } from './date-picker/date-picker';
