package com.crm.customer.customer.repository;

import com.crm.customer.contact.entity.ContactMedium;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.entity.Individual;
import com.crm.customer.customer.entity.Party;
import com.crm.customer.customer.entity.PartyRole;
import com.crm.customer.lookup.LookupContract;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * FR-CUST-01 list/filter predicate (ADR-005):
 * - with NO criteria, the browse mode returns every ACTIVE customer (AC-CUST-01-00);
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
 *
 * The row query FETCH-joins the whole to-one detail graph (partyRole → role/party →
 * individual/contactMedium) because every returned row is mapped to the full
 * CustomerDetailResponse: without the fetch, the default-EAGER to-one associations
 * would load with one extra SELECT per row (classic N+1). The count query keeps plain
 * joins — JPA forbids fetch joins there, and to-one fetch joins never change row
 * counts, so pagination stays correct.
 */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<Customer> search(String firstName, String lastName, String nationalityId,
                                                 Long customerNumber, String gsmNumber) {
        return (root, query, cb) -> {
            boolean countQuery = query.getResultType() == Long.class || query.getResultType() == long.class;

            Join<Customer, PartyRole> partyRole;
            Join<PartyRole, Party> party;
            Join<Party, Individual> individual;
            Join<Party, ContactMedium> contact;
            if (countQuery) {
                partyRole = root.join("partyRole", JoinType.INNER);
                party = partyRole.join("party", JoinType.INNER);
                individual = party.join("individual", JoinType.INNER);
                contact = party.join("contactMedium", JoinType.LEFT);
            } else {
                // Hibernate's fetch nodes implement Join, so the same instances serve
                // both as fetch graph and as predicate/sort paths.
                Fetch<Customer, PartyRole> partyRoleFetch = root.fetch("partyRole", JoinType.INNER);
                partyRoleFetch.fetch("role", JoinType.INNER);
                Fetch<PartyRole, Party> partyFetch = partyRoleFetch.fetch("party", JoinType.INNER);
                Fetch<Party, Individual> individualFetch = partyFetch.fetch("individual", JoinType.INNER);
                Fetch<Party, ContactMedium> contactFetch = partyFetch.fetch("contactMedium", JoinType.LEFT);
                partyRole = asJoin(partyRoleFetch);
                party = asJoin(partyFetch);
                individual = asJoin(individualFetch);
                contact = asJoin(contactFetch);
            }

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

            // ADR-005 browse mode: no filled criterion means "list all active customers",
            // not "match nothing" (an empty cb.or() is FALSE, so it must be skipped).
            if (criteria.isEmpty()) {
                return activeOnly;
            }
            Predicate anyCriterion = cb.or(criteria.toArray(new Predicate[0]));
            return cb.and(activeOnly, anyCriterion);
        };
    }

    @SuppressWarnings("unchecked")
    private static <Z, X> Join<Z, X> asJoin(Fetch<Z, X> fetch) {
        return (Join<Z, X>) fetch;
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
