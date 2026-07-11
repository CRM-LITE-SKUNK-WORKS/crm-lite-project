package com.crm.customer.customer.dto.request;

/**
 * FR-CUST-04: the update screen edits the demographic set only (addresses and
 * contact medium have their own endpoints). Same fields and validation as the
 * create flow's demographic block.
 */
public class CustomerUpdateRequest extends DemographicRequest {
}
