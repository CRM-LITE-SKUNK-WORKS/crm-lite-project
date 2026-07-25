/** Customer contract types (docs/api/customer-service.md). Barrel for the model
 *  layer — the data services and (later) screens import from here. */
export type {
  Gender,
  CustomerDetailResponse,
  DemographicRequest,
  CreateCustomerRequest,
  UpdateCustomerRequest,
} from './customer.model';
export type { AddressRequest, AddressResponse } from './address.model';
export type { ContactMediumRequest, ContactMediumResponse } from './contact.model';
export { type SpringPage, type PageResult, toPageResult } from './page.model';
export {
  type CustomerSearchCriteria,
  type PageRequest,
  type PageSize,
  PAGE_SIZE_OPTIONS,
  DEFAULT_PAGE_SIZE,
} from './customer-search.model';
