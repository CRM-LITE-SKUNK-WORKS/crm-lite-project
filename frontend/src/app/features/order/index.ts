/**
 * Public surface of features/order — the ORDER DOMAIN (FR-SALE-01/02).
 *
 * Consumed by `features/customer/sales/` (the wizard), which is the SAME
 * one-way sibling direction FE-ADR-003 §Consequences already sanctions for
 * `features/account/` (§4.24) and `features/product/` (§4.28/3): customer is
 * the host feature that composes siblings. Nothing here imports customer.
 *
 * There is deliberately no order-list or order-cancel surface — neither
 * endpoint exists (scope §2B.5/§2B.6).
 */
export type {
  CharacteristicValueRequest,
  OrderItemRequest,
  SubmitOrderRequest,
  OrderItemResponse,
  OrderResponse,
  BasketItem,
} from './model';
export { REQUIRED_SERVICE_TYPES } from './model';
export { OrderApiService } from './data/order-api.service';
export { SaleBasketStore, type BasketCompositionIssue } from './state/sale-basket.store';
export { OrderSubmitStore } from './state/order-submit.store';
