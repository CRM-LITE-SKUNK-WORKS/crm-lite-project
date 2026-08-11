/** Order contract types (docs/api/order-service.md) + the client-only basket. */
export type {
  CharacteristicValueRequest,
  OrderItemRequest,
  CreateDraftRequest,
  SubmitDraftRequest,
  ProcessingStatus,
  OrderStatusResponse,
  OrderItemResponse,
  OrderResponse,
  BasketItem,
  BasketGroup,
  BasketCampaignGroup,
  BasketOfferGroup,
} from './order.model';
export { REQUIRED_SERVICE_TYPES } from './order.model';
