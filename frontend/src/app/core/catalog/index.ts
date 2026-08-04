/** Public surface of core/catalog — the product catalog (offers, campaigns,
 *  characteristics) shared by features/product and the features/order sale
 *  wizard (FE-ADR-003: features never import siblings; scope §1.6). */
export type {
  ServiceType,
  CharacteristicDataType,
  OfferResponse,
  CampaignOffer,
  CampaignResponse,
  CharacteristicResponse,
} from './catalog.model';
export { CatalogApiService } from './catalog-api.service';
export {
  characteristicErrorKey,
  characteristicValidators,
  charFormatValidator,
  charNotPastValidator,
  charRequiredValidator,
  isBlankValue,
  matchesDataType,
  todayIso,
} from './characteristic-validation';
