// Copyright © 2016 HSL <https://www.hsl.fi>
// This program is dual-licensed under the EUPL v1.2 and AGPLv3 licenses.

package fi.hsl.parkandride.core.service;

import fi.hsl.parkandride.core.back.ContactRepository;
import fi.hsl.parkandride.core.back.FacilityRepository;
import fi.hsl.parkandride.core.back.PredictionRepository;
import fi.hsl.parkandride.core.back.UtilizationRepository;
import fi.hsl.parkandride.core.domain.*;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

import static fi.hsl.parkandride.core.domain.Permission.*;
import static fi.hsl.parkandride.core.service.AuthenticationService.authorize;

public class FacilityService {
    private static final Logger logger = LoggerFactory.getLogger(FacilityService.class);

    private final FacilityRepository repository;
    private final UtilizationRepository utilizationRepository;
    private final ContactRepository contactRepository;
    private final ValidationService validationService;
    private final PredictionService predictionService;

    public FacilityService(FacilityRepository repository, UtilizationRepository utilizationRepository, ContactRepository contactRepository, ValidationService validationService, PredictionService predictionService) {
        this.repository = repository;
        this.utilizationRepository = utilizationRepository;
        this.contactRepository = contactRepository;
        this.validationService = validationService;
        this.predictionService = predictionService;
    }

    @TransactionalWrite
    public Facility createFacility(Facility facility, User currentUser) {
        authorize(currentUser, facility, FACILITY_CREATE);
        validate(facility);

        return getFacility(repository.insertFacility(facility));
    }

    @TransactionalWrite
    public Facility updateFacility(long facilityId, Facility facility, User currentUser) {
        // User has update right to the input data...
        authorize(currentUser, facility, FACILITY_UPDATE);
        // ...and to the facility being updated
        Facility oldFacility = repository.getFacilityForUpdate(facilityId);
        authorize(currentUser, oldFacility, FACILITY_UPDATE);

        validate(facility);

        repository.updateFacility(facilityId, facility, oldFacility);
        return getFacility(facilityId);
    }

    private void validate(Facility facility) {
        Collection<Violation> violations = new ArrayList<>();
        validationService.validate(facility, violations);
        CapacityPricingValidator.validateAndNormalize(facility, violations);
        validateContact(facility.operatorId, facility.contacts.emergency, "emergency", violations);
        validateContact(facility.operatorId, facility.contacts.operator, "operator", violations);
        validateContact(facility.operatorId, facility.contacts.service, "service", violations);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private void validateContact(Long facilityOperatorId, Long contactId, String contactType, Collection<Violation> violations) {
        if (contactId != null) {
            Contact contact = contactRepository.getContact(contactId);
            if (contact == null) {
                violations.add(new Violation("NotFound", "contacts." + contactType, "contact not found"));
            } else if (contact.operatorId != null && !contact.operatorId.equals(facilityOperatorId)) {
                violations.add(new Violation("OperatorMismatch", "contacts." + contactType, "operator should match facility operator"));
            }
        }
    }

    @TransactionalRead
    public Facility getFacility(long id) {
        return repository.getFacility(id);
    }

    @TransactionalRead
    public SearchResults<FacilityInfo> search(PageableFacilitySearch search) {
        return repository.findFacilities(search);
    }

    @TransactionalRead
    public FacilitySummary summarize(FacilitySearch search) {
        return repository.summarizeFacilities(search);
    }

    @TransactionalWrite
    public void registerUtilization(long facilityId, List<Utilization> utilization, User currentUser) {
        FacilityInfo facility = repository.getFacilityInfo(facilityId);
        authorize(currentUser, facility, FACILITY_UTILIZATION_UPDATE);

        initUtilizationDefaults(facility, utilization);
        validateUtilizations(facilityId, utilization);
        autoUpdateFacilityCapacity(utilization);

        checkUtilizationApplicability(facility, utilization);

        utilizationRepository.insertUtilizations(utilization);
        predictionService.signalUpdateNeeded(utilization);
    }

    /**
     * Logs a warning for each utilization whose usage or capacity type is not included in the facility info
     * or whose number of spaces available exceeds the corresponding built capacity.
     */
    private void checkUtilizationApplicability(FacilityInfo info, List<Utilization> utilization) {
        utilization.stream()
                .filter(u -> !info.usages.contains(u.usage) ||
                        info.builtCapacity.getOrDefault(u.capacityType, -1) < u.spacesAvailable
                )
                .forEach(u -> logger.warn(
                        "Unexpected utilization for facility id={}: usage {} not found in {} or spaces available ({}) exceeds built capacity ({})",
                        info.id, u.usage, info.usages, u.spacesAvailable, info.builtCapacity.get(u.capacityType)
                ));
    }

    private static void initUtilizationDefaults(FacilityInfo facility, List<Utilization> utilization) {
        for (Utilization u : utilization) {
            if (u.facilityId == null) {
                u.facilityId = facility.id;
            }
            if (u.capacity == null) {
                u.capacity = facility.builtCapacity.getOrDefault(u.capacityType, u.spacesAvailable);
            }
        }
    }

    public void validateUtilizations(long facilityId, List<Utilization> utilizations) {
        for (int i = 0; i < utilizations.size(); i++) {
            List<Violation> violations = Violation.withPathPrefix("[" + i + "].", validateUtilization(utilizations.get(i), facilityId));
            if (!violations.isEmpty()) {
                throw new ValidationException(violations);
            }
        }
    }

    private List<Violation> validateUtilization(Utilization u, long expectedFacilityId) {
        List<Violation> violations = new ArrayList<>();
        validationService.validate(u, violations);
        if (!Objects.equals(u.facilityId, expectedFacilityId)) {
            violations.add(new Violation("NotEqual", "facilityId", "Expected to be " + expectedFacilityId + " but was " + u.facilityId));
        }
        if (isFarIntoFuture(u.timestamp)) {
            violations.add(new Violation("NotFuture", "timestamp", u.timestamp + " is too far into future; the current time is " + DateTime.now()));
        }
        return violations;
    }

    private static boolean isFarIntoFuture(DateTime time) {
        Seconds gracePeriod = PredictionRepository.PREDICTION_RESOLUTION.toStandardSeconds().dividedBy(2);
        DateTime timeLimit = DateTime.now().plus(gracePeriod);
        return time != null && time.isAfter(timeLimit);
    }

    private void autoUpdateFacilityCapacity(List<Utilization> utilization) {
        for (Utilization u : utilization) {
            Facility facility = repository.getFacility(u.facilityId);

            Integer builtCapacity = facility.builtCapacity.get(u.capacityType);
            if (builtCapacity == null) {
                continue;
            }
            if (builtCapacity < u.capacity) {
                builtCapacity = u.capacity;
                facility.builtCapacity.put(u.capacityType, builtCapacity);
                repository.updateFacility(facility.id, facility);
            }

            Predicate<UnavailableCapacity> matchesUtilization = uc -> uc.capacityType.equals(u.capacityType) && uc.usage.equals(u.usage);
            int unavailableCapacity = facility.unavailableCapacities.stream()
                    .filter(matchesUtilization)
                    .map(uc -> uc.capacity)
                    .findFirst()
                    .orElse(0);
            if (builtCapacity - unavailableCapacity != u.capacity) {
                facility.unavailableCapacities.removeIf(matchesUtilization);
                facility.unavailableCapacities.add(new UnavailableCapacity(u.capacityType, u.usage, builtCapacity - u.capacity));
                repository.updateFacility(facility.id, facility);
            }
        }
    }

    @TransactionalRead
    public Set<Utilization> findLatestUtilization(Long... facilityIds) {
        return utilizationRepository.findLatestUtilization(facilityIds);
    }
}
