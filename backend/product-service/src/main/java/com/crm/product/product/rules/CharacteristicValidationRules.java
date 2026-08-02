package com.crm.product.product.rules;

import com.crm.product.catalog.entity.ProductSpecChar;
import com.crm.product.catalog.entity.ProductSpecCharUse;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AC-SALE-01-18/19 characteristic validation — the "S6" rule that this logic stays
 * in product-service (ADR-015 §6): only this service knows a characteristic's
 * declared {@code data_type}, so only it can decide whether a value fits.
 * order-service forwards raw values and writes no validation of its own.
 *
 * <p>Same reasoning as {@link BasketValidationRules}: the FR states these as
 * Product-Configuration screen checks on LBL-NEXT, but a screen-only check is not a
 * check.
 */
@Component
public class CharacteristicValidationRules {

    /** The workbook's four data types (PROD_SPEC_CHAR.data_type). */
    private static final String TYPE_NUMBER = "NUMBER";
    private static final String TYPE_BOOLEAN = "BOOLEAN";
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_DATE = "DATE";

    /**
     * Validates the values submitted for one product against its offer's
     * characteristic schema and returns them keyed by characteristic id, ready to
     * persist.
     *
     * <p>Rules, in the order a user would hit them:
     * <ol>
     *   <li>a value for a characteristic the offer does not use → 400
     *       MSG-VALIDATION-ERROR (not a MSG-VAL-CHAR-* key: the analyst keys describe
     *       a field the user filled in wrongly, and this field was never on the
     *       screen — it is a client defect, not user input);
     *   <li>a mandatory characteristic missing or blank → MSG-VAL-CHAR-REQUIRED;
     *   <li>a present value that does not parse as its data type → MSG-VAL-CHAR-FORMAT.
     * </ol>
     *
     * <p>A blank <b>optional</b> value is legal and is simply not persisted — writing
     * an empty prod_char_val row would fabricate a configuration the user never made.
     */
    public Map<Long, String> validate(Long offerId, List<ProductSpecCharUse> schema, Map<Long, String> submitted) {
        Map<Long, ProductSpecChar> allowed = new LinkedHashMap<>();
        Map<Long, Boolean> mandatory = new LinkedHashMap<>();
        for (ProductSpecCharUse use : schema) {
            allowed.put(use.getCharacteristic().getId(), use.getCharacteristic());
            mandatory.put(use.getCharacteristic().getId(), use.isMandatory());
        }

        for (Long characteristicId : submitted.keySet()) {
            if (!allowed.containsKey(characteristicId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                        "Characteristic " + characteristicId + " is not configurable for offer " + offerId);
            }
        }

        Map<Long, String> accepted = new LinkedHashMap<>();
        for (Map.Entry<Long, ProductSpecChar> entry : allowed.entrySet()) {
            Long characteristicId = entry.getKey();
            ProductSpecChar characteristic = entry.getValue();
            String value = submitted.get(characteristicId);
            boolean blank = value == null || value.isBlank();

            if (blank) {
                if (Boolean.TRUE.equals(mandatory.get(characteristicId))) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VAL_CHAR_REQUIRED,
                            "'" + characteristic.getName() + "' is required");
                }
                continue;
            }

            String trimmed = value.trim();
            if (!matchesDataType(characteristic.getDataType(), trimmed)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VAL_CHAR_FORMAT,
                        "'" + characteristic.getName() + "' must be a valid " + characteristic.getDataType()
                                + " value");
            }
            accepted.put(characteristicId, trimmed);
        }
        return accepted;
    }

    /**
     * An unknown data_type is treated as UNVALIDATABLE and rejected rather than
     * waved through: the workbook defines exactly four, so a fifth means the catalog
     * grew a type this code has never seen — accepting it would persist a value
     * nothing can interpret.
     */
    private boolean matchesDataType(String dataType, String value) {
        return switch (dataType == null ? "" : dataType.toUpperCase()) {
            case TYPE_TEXT -> true;
            case TYPE_NUMBER -> isNumber(value);
            // Deliberately strict: "1"/"0"/"yes" are NOT booleans here.
            // Boolean.parseBoolean would silently turn every typo into `false`.
            case TYPE_BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case TYPE_DATE -> isIsoDate(value);
            default -> false;
        };
    }

    private boolean isNumber(String value) {
        try {
            new java.math.BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
