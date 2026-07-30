package com.crm.product.product.mapper;

import com.crm.product.customer.CustomerAddress;
import com.crm.product.product.ProductContract;
import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import com.crm.product.product.dto.response.ServiceAddressResponse;
import com.crm.product.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    /**
     * Must run inside a transaction (the campaign association is read here).
     * productStatus is derived from the local soft-delete invariant, never stored;
     * campaign fields are null (not "-") when the product was bought outside a
     * campaign — rendering is the frontend's job.
     */
    public ProductRowResponse toRowResponse(Product product) {
        boolean inCampaign = product.getCampaign() != null;
        return new ProductRowResponse(
                product.getId(),
                product.getName(),
                inCampaign ? product.getCampaign().getName() : null,
                inCampaign ? product.getCampaign().getCampaignCode() : null,
                product.isActive() ? ProductContract.STATUS_LABEL_ACTIVE : ProductContract.STATUS_LABEL_PASSIVE);
    }

    /** Must run inside a transaction (offer/spec/campaign associations are read here). */
    public ProductDetailResponse toDetailResponse(Product product, ServiceAddressResponse serviceAddress) {
        return new ProductDetailResponse(
                product.getOffer().getName(),
                product.getOffer().getId(),
                product.getSpec().getId(),
                product.getCampaign() == null ? null : product.getCampaign().getName(),
                serviceAddress);
    }

    public ServiceAddressResponse toServiceAddressResponse(CustomerAddress address) {
        return new ServiceAddressResponse(
                address.addressId(),
                address.street(),
                address.houseFlatNumber(),
                address.districtName(),
                address.cityName());
    }
}
