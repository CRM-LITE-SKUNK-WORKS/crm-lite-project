package com.crm.product.product.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.product.catalog.entity.ProductSpecChar;
import com.crm.product.catalog.entity.ProductSpecCharUse;
import com.crm.product.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The data-type matrix of AC-SALE-01-19, unit-tested because two of its decisions
 * are deliberate and cannot be reached through the integration suite: the strict
 * BOOLEAN rule, and the refusal to accept a data type this code has never seen
 * (no seed row carries one).
 */
class CharacteristicValidationRulesTest {

    private final CharacteristicValidationRules rules = new CharacteristicValidationRules();

    private ProductSpecCharUse use(long id, String name, String dataType, boolean mandatory) {
        ProductSpecChar characteristic = new ProductSpecChar();
        characteristic.setId(id);
        characteristic.setName(name);
        characteristic.setDataType(dataType);
        ProductSpecCharUse use = new ProductSpecCharUse();
        use.setCharacteristic(characteristic);
        use.setMandatory(mandatory);
        return use;
    }

    @Test
    @DisplayName("accepted values are trimmed and returned; a blank OPTIONAL value is skipped, not persisted empty")
    void acceptsValidValues() {
        List<ProductSpecCharUse> schema = List.of(
                use(1, "Download Speed", "NUMBER", true),
                use(2, "Static IP", "BOOLEAN", false),
                use(3, "MAC Address", "TEXT", false),
                use(4, "Commitment End Date", "DATE", false));

        Map<Long, String> accepted = rules.validate(1L, schema, Map.of(
                1L, "  16.5 ", 2L, "TRUE", 3L, "AA:BB", 4L, "2027-12-31"));

        assertThat(accepted).containsExactly(
                Map.entry(1L, "16.5"), Map.entry(2L, "TRUE"), Map.entry(3L, "AA:BB"), Map.entry(4L, "2027-12-31"));

        // Writing an empty row would fabricate a configuration the user never made.
        Map<Long, String> withBlanks = rules.validate(1L, schema, Map.of(1L, "8", 2L, "   "));
        assertThat(withBlanks).containsOnlyKeys(1L);
    }

    @Test
    @DisplayName("BOOLEAN is strict: 1/0/yes are format errors, not silently coerced to false")
    void booleanIsStrict() {
        List<ProductSpecCharUse> schema = List.of(use(2, "Static IP", "BOOLEAN", true));
        assertThat(rules.validate(1L, schema, Map.of(2L, "false"))).containsEntry(2L, "false");

        for (String bad : List.of("1", "0", "yes", "evet")) {
            assertThatThrownBy(() -> rules.validate(1L, schema, Map.of(2L, bad)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "MSG-VAL-CHAR-FORMAT");
        }
    }

    @Test
    @DisplayName("an unknown data_type is REJECTED, not waved through — nothing could interpret the stored value")
    void unknownDataTypeIsRejected() {
        // The workbook defines exactly four types. A fifth means the catalog grew one
        // this code has never seen; accepting it would persist an uninterpretable value.
        List<ProductSpecCharUse> schema = List.of(use(9, "Bandwidth Profile", "JSON", true));
        assertThatThrownBy(() -> rules.validate(1L, schema, Map.of(9L, "{\"a\":1}")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("messageKey", "MSG-VAL-CHAR-FORMAT");
    }

    @Test
    @DisplayName("a mandatory field reports MSG-VAL-CHAR-REQUIRED whether it was blank or absent")
    void mandatoryRules() {
        List<ProductSpecCharUse> schema = List.of(use(1, "Download Speed", "NUMBER", true));

        assertThatThrownBy(() -> rules.validate(1L, schema, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("messageKey", "MSG-VAL-CHAR-REQUIRED")
                .hasMessageContaining("Download Speed");

        assertThatThrownBy(() -> rules.validate(1L, schema, Map.of(1L, " ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("messageKey", "MSG-VAL-CHAR-REQUIRED");
    }

    @Test
    @DisplayName("an offer with no characteristics accepts an empty submission (AC-SALE-01-21)")
    void offerWithoutCharacteristics() {
        assertThat(rules.validate(3L, List.of(), Map.of())).isEmpty();
    }
}
