package org.hisp.dhis.fhir.config;

import java.util.List;
import java.util.Map;

// Single source of truth for the five sync type keys, shared by SyncTriggerController,
// PreviewController, and ScheduleController -- previously each of these either duplicated
// this list/map or (for PreviewController) a parallel switch statement; a third consumer
// (scheduling) made keeping them in sync by hand too easy to get wrong.
public final class SyncTypes
{
    public static final String IMMUNIZATION_TO_FHIR = "immunization-to-fhir";
    public static final String IMMUNIZATION_TO_DHIS2 = "immunization-to-dhis2";
    public static final String ORG_UNITS_TO_FHIR = "org-units-to-fhir";
    public static final String OPTION_SETS_TO_FHIR = "option-sets-to-fhir";
    public static final String TRACKED_ENTITIES_TO_FHIR = "tracked-entities-to-fhir";

    public static final List<String> ALL = List.of(
        IMMUNIZATION_TO_FHIR, IMMUNIZATION_TO_DHIS2, ORG_UNITS_TO_FHIR, OPTION_SETS_TO_FHIR,
        TRACKED_ENTITIES_TO_FHIR
    );

    public static final Map<String, String> DIRECT_ENDPOINT_BY_TYPE = Map.of(
        IMMUNIZATION_TO_FHIR, "direct:dhis2ToFhirSync",
        IMMUNIZATION_TO_DHIS2, "direct:fhirToDhis2Sync",
        ORG_UNITS_TO_FHIR, "direct:orgUnitsToFhirSync",
        OPTION_SETS_TO_FHIR, "direct:optionSetsToFhirSync",
        TRACKED_ENTITIES_TO_FHIR, "direct:trackedEntitiesToFhirSync"
    );

    private SyncTypes()
    {
    }
}
