package com.crm.customer.customer.repository;

import com.crm.customer.contact.entity.ContactMedium;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.entity.Individual;
import com.crm.customer.customer.entity.Party;
import com.crm.customer.customer.entity.PartyRole;
import com.crm.customer.lookup.LookupContract;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * FR-CUST-01 / KR-01 search predicate:
 * - firstName matches WORD-START, case-insensitively, in the First + Middle Name
 *   combination ("Kemal" finds "Ali Kemal", "li" does NOT find "Ali"/"Velihan");
 * - lastName matches word-start in Last Name only;
 * - both present => AND-ed into one name criterion;
 * - gsmNumber is a PREFIX match on the contact medium's mobile phone;
 * - nationalityId and customerNumber (public "Customer ID") match exactly;
 * - the filled criterion groups are OR-ed;
 * - only ACTIVE customers return: status_id = ACTV (contract ID, local — no remote
 *   call per query, see ADR-002) AND deleted_date IS NULL.
 *
 * All joins are to-one, so the query cannot fan out into duplicate customers —
 * results are distinct by construction and no DISTINCT is needed (Postgres rejects
 * SELECT DISTINCT with ORDER BY on joined columns not in the select list).
 */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<Customer> search(String firstName, String lastName, String nationalityId,
                                                 Long customerNumber, String gsmNumber) {
        return (root, query, cb) -> {
            Join<Customer, PartyRole> partyRole = root.join("partyRole", JoinType.INNER);
            Join<PartyRole, Party> party = partyRole.join("party", JoinType.INNER);
            Join<Party, Individual> individual = party.join("individual", JoinType.INNER);
            partyRole.join("role", JoinType.INNER);
            Join<Party, ContactMedium> contact = party.join("contactMedium", JoinType.LEFT);

            Predicate activeOnly = cb.and(
                    cb.equal(root.get("statusId"), LookupContract.STATUS_ACTIVE_ID),
                    cb.isNull(root.get("deletedDate")));

            List<Predicate> criteria = new ArrayList<>();

            boolean hasFirstName = StringUtils.hasText(firstName);
            boolean hasLastName = StringUtils.hasText(lastName);
            if (hasFirstName || hasLastName) {
                Predicate namePredicate = cb.conjunction();
                if (hasFirstName) {
                    // first + " " + coalesce(middle, ""), lowercased: word-start matching
                    // over the combined name handles both middle-name column values and
                    // multi-word first names ("Ali Kemal").
                    Expression<String> combined = cb.lower(cb.concat(
                            cb.concat(individual.get("firstName"), cb.literal(" ")),
                            cb.coalesce(individual.get("middleName"), cb.literal(""))));
                    namePredicate = cb.and(namePredicate, wordStart(cb, combined, firstName));
                }
                if (hasLastName) {
                    namePredicate = cb.and(namePredicate,
                            wordStart(cb, cb.lower(individual.get("lastName")), lastName));
                }
                criteria.add(namePredicate);
            }

            if (StringUtils.hasText(nationalityId)) {
                criteria.add(cb.equal(individual.get("nationalityId"), nationalityId));
            }

            if (customerNumber != null) {
                criteria.add(cb.equal(root.get("customerNumber"), customerNumber));
            }

            if (StringUtils.hasText(gsmNumber)) {
                criteria.add(cb.and(
                        cb.isNull(contact.get("deletedDate")),
                        cb.like(contact.get("mobilePhone"), escapeLike(gsmNumber) + "%", '\\')));
            }

            Predicate anyCriterion = cb.or(criteria.toArray(new Predicate[0]));
            return cb.and(activeOnly, anyCriterion);
        };
    }

    /** value matches at the start of the string OR at the start of any later word. */
    private static Predicate wordStart(CriteriaBuilder cb, Expression<String> haystack, String value) {
        String needle = escapeLike(value.toLowerCase());
        return cb.or(
                cb.like(haystack, needle + "%", '\\'),
                cb.like(haystack, "% " + needle + "%", '\\'));
    }

    /** Escapes LIKE wildcards in user input so they match literally. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
