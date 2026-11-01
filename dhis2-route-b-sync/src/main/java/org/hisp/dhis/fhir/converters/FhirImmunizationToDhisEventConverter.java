package org.hisp.dhis.fhir.converters;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.VaccineConfig;
import org.hisp.dhis.fhir.domain.SyncLog;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Route A: FHIR -> DHIS2 (the reverse of VaccinationEventToImmunizationConverter).
// A plain Spring bean invoked via .bean(...) from the route, not a Camel type converter --
// same lesson learned on the DHIS2-to-FHIR side: keep conversion logic in a directly-callable
// bean rather than fighting Camel's type-converter registry.
@Component
@RequiredArgsConstructor
public class FhirImmunizationToDhisEventConverter
{
    private static final String SYNC_ORIGIN_TAG_SYSTEM = "https://dhis2.org/fhir/sync-origin";
    private static final String SYNC_ORIGIN_TAG_CODE = "dhis2-sync";
    private static final String CVX_SYSTEM = "http://hl7.org/fhir/sid/cvx";

    private static final String ORG_UNIT = "DiszpKrYNg8";

    private final IGenericClient fhirClient;

    private final Dhis2Client dhis2Client;

    private final SyncLogRepository syncLogRepository;

    public Map<String, Object> immunizationToDhisEventPayload( Immunization immunization )
    {
        String immunizationId = immunization.getIdElement().getIdPart();

        // Origin check -- skip anything this same service already pushed into FHIR via
        // Route B, otherwise this would create a DHIS2 event from data that came from DHIS2
        // in the first place (the sync loop described in the plan's origin-tracking section).
        // Using the explicit stream form rather than Meta.getTag(system, code): that overload
        // resolves via a generic-interface bridge method and was observed to never match here.
        boolean isOwnOutput = immunization.getMeta().getTag().stream()
            .anyMatch( tag -> SYNC_ORIGIN_TAG_SYSTEM.equals( tag.getSystem() )
                && SYNC_ORIGIN_TAG_CODE.equals( tag.getCode() ) );
        if ( isOwnOutput )
        {
            syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "skipped-own-output", null ) );
            return null;
        }

        Optional<VaccineConfig> vaccine = immunization.getVaccineCode().getCoding().stream()
            .filter( coding -> CVX_SYSTEM.equals( coding.getSystem() ) )
            .map( Coding::getCode )
            .findFirst()
            .flatMap( VaccineCatalog::byCvxCode );

        if ( vaccine.isEmpty() )
        {
            syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "skipped-unknown-vaccine", null ) );
            return null;
        }

        String patientReference = immunization.getPatient().getReference();
        String patientId = patientReference.substring( patientReference.lastIndexOf( '/' ) + 1 );
        Patient patient = fhirClient.read().resource( Patient.class ).withId( patientId ).execute();

        // Only patients already known to DHIS2 (carrying a trackedEntity identifier, the
        // same convention Route B writes) can be mapped -- creating a brand-new DHIS2
        // tracked entity for an unknown patient is out of scope for this first version.
        if ( patient.getIdentifier().isEmpty() )
        {
            syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "skipped-not-mapped", null ) );
            return null;
        }
        String trackedEntityId = patient.getIdentifierFirstRep().getValue();

        // The Birth and Baby Postnatal stages are both non-repeatable -- DHIS2 rejects a
        // second new event for an enrollment that already has one there (E1039). The
        // existing event must be updated instead of creating a new one, so resolve both its
        // id and its enrollment, scoped to whichever stage this vaccine's data element lives in.
        Iterable<Map> existingEvents = dhis2Client.get( "tracker/events" )
            .withFields( "event,enrollment" )
            .withoutPaging()
            .withParameter( "trackedEntity", trackedEntityId )
            .withParameter( "program", VaccineCatalog.PROGRAM )
            .withParameter( "programStage", vaccine.get().getProgramStageId() )
            .transfer()
            .returnAs( Map.class, "events" );

        String existingEventId = null;
        String enrollmentId = null;
        for ( Map existingEvent : existingEvents )
        {
            existingEventId = (String) existingEvent.get( "event" );
            enrollmentId = (String) existingEvent.get( "enrollment" );
            break;
        }
        if ( existingEventId == null )
        {
            syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "skipped-no-existing-event", null ) );
            return null;
        }

        Map<String, Object> dataValue = new LinkedHashMap<>();
        dataValue.put( "dataElement", vaccine.get().getDataElementId() );
        dataValue.put( "value", vaccine.get().valueWhenGiven() );

        Map<String, Object> event = new LinkedHashMap<>();
        event.put( "event", existingEventId );
        event.put( "program", VaccineCatalog.PROGRAM );
        event.put( "programStage", vaccine.get().getProgramStageId() );
        event.put( "orgUnit", ORG_UNIT );
        event.put( "trackedEntity", trackedEntityId );
        event.put( "enrollment", enrollmentId );
        event.put( "occurredAt", immunization.getOccurrenceDateTimeType().getValueAsString() );
        event.put( "status", "COMPLETED" );
        event.put( "dataValues", List.of( dataValue ) );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put( "events", List.of( event ) );

        syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "updated",
            "DHIS2 event/" + existingEventId + " (" + vaccine.get().getCvxDisplay()
                + ", trackedEntity " + trackedEntityId + ")" ) );

        return payload;
    }
}
