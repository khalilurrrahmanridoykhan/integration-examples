package org.hisp.dhis.fhir.converters;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.VaccineConfig;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.domain.SyncLog;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
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

    private static final String PERSON_TRACKED_ENTITY_TYPE = "nEenWmSyUEp";
    private static final String GENDER_ATTRIBUTE = "cejWyOfXge6";
    private static final String FIRST_NAME_ATTRIBUTE = "w75KJ2mc4zz";
    private static final String LAST_NAME_ATTRIBUTE = "zDhUuAYrxNC";

    private final IGenericClient fhirClient;

    private final Dhis2Client dhis2Client;

    private final DhisProperties dhisProperties;

    private final VaccineCatalog vaccineCatalog;

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
            .flatMap( vaccineCatalog::byCvxCode );

        if ( vaccine.isEmpty() )
        {
            syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "skipped-unknown-vaccine", null ) );
            return null;
        }

        String patientReference = immunization.getPatient().getReference();
        String patientId = patientReference.substring( patientReference.lastIndexOf( '/' ) + 1 );
        Patient patient = fhirClient.read().resource( Patient.class ).withId( patientId ).execute();

        // A patient with no DHIS2 identifier yet is a genuinely new person as far as DHIS2
        // is concerned -- create a tracked entity, enrollment, and this vaccine's event in
        // one atomic Tracker import (confirmed live that DHIS2 supports nested creation),
        // then write the new id back onto the FHIR Patient so the next run recognizes them.
        if ( patient.getIdentifier().isEmpty() )
        {
            String newTrackedEntityId = createTrackedEntityWithEnrollmentAndEvent( patient, immunization, vaccine.get() );
            if ( newTrackedEntityId == null )
            {
                syncLogRepository.save(
                    new SyncLog( "fhir-to-dhis2", immunizationId, "skipped-new-patient-create-failed", null ) );
                return null;
            }

            // The nested import above already created the event -- nothing left to POST.
            syncLogRepository.save( new SyncLog( "fhir-to-dhis2", immunizationId, "created-new-tracked-entity",
                "DHIS2 trackedEntity/" + newTrackedEntityId + " (" + vaccine.get().getCvxDisplay() + ")" ) );
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
            .withParameter( "program", vaccineCatalog.getProgram() )
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
        event.put( "program", vaccineCatalog.getProgram() );
        event.put( "programStage", vaccine.get().getProgramStageId() );
        event.put( "orgUnit", dhisProperties.getOrgUnit() );
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

    // Confirmed live against the real DHIS2 demo: a single tracker import can create a
    // trackedEntity, its enrollment, and a first event all nested in one atomic call, with
    // DHIS2 assigning the new trackedEntity id. This call is made directly (not via the
    // Camel dhis2:// route step) because the id it returns is needed synchronously, right
    // here, to write back onto the FHIR Patient -- the route's own POST step doesn't read
    // the response at all.
    @SuppressWarnings( "unchecked" )
    private String createTrackedEntityWithEnrollmentAndEvent( Patient patient, Immunization immunization,
        VaccineConfig vaccine )
    {
        Map<String, Object> dataValue = new LinkedHashMap<>();
        dataValue.put( "dataElement", vaccine.getDataElementId() );
        dataValue.put( "value", vaccine.valueWhenGiven() );

        String occurredAt = immunization.getOccurrenceDateTimeType().getValueAsString();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put( "program", vaccineCatalog.getProgram() );
        event.put( "programStage", vaccine.getProgramStageId() );
        event.put( "orgUnit", dhisProperties.getOrgUnit() );
        event.put( "occurredAt", occurredAt );
        event.put( "status", "COMPLETED" );
        event.put( "dataValues", List.of( dataValue ) );

        Map<String, Object> enrollment = new LinkedHashMap<>();
        enrollment.put( "program", vaccineCatalog.getProgram() );
        enrollment.put( "orgUnit", dhisProperties.getOrgUnit() );
        enrollment.put( "status", "ACTIVE" );
        enrollment.put( "enrolledAt", LocalDate.now().toString() );
        enrollment.put( "occurredAt", occurredAt );
        enrollment.put( "events", List.of( event ) );

        Map<String, Object> trackedEntity = new LinkedHashMap<>();
        trackedEntity.put( "orgUnit", dhisProperties.getOrgUnit() );
        trackedEntity.put( "trackedEntityType", PERSON_TRACKED_ENTITY_TYPE );
        trackedEntity.put( "attributes", demographicAttributes( patient ) );
        trackedEntity.put( "enrollments", List.of( enrollment ) );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put( "trackedEntities", List.of( trackedEntity ) );

        // async=false so the created trackedEntity id is available in this same response --
        // the usual "Tracker job added" response (see Route A's history) only gives a job
        // id, not the result, which is no good when the id is needed right here.
        Map<String, Object> response = dhis2Client.post( "tracker" )
            .withResource( payload )
            .withParameter( "async", "false" )
            .transfer()
            .returnAs( Map.class );

        String newTrackedEntityId = extractNewTrackedEntityId( response );
        if ( newTrackedEntityId == null )
        {
            return null;
        }

        patient.getIdentifier().add(
            new Identifier().setSystem( "https://play.im.dhis2.org/dev/trackedEntityInstances" )
                .setValue( newTrackedEntityId ) );
        fhirClient.update().resource( patient ).execute();

        return newTrackedEntityId;
    }

    private List<Map<String, Object>> demographicAttributes( Patient patient )
    {
        List<Map<String, Object>> attributes = new ArrayList<>();

        String firstName = patient.getNameFirstRep().getGivenAsSingleString();
        if ( firstName != null && !firstName.isBlank() )
        {
            attributes.add( Map.of( "attribute", FIRST_NAME_ATTRIBUTE, "value", firstName ) );
        }

        String lastName = patient.getNameFirstRep().getFamily();
        if ( lastName != null && !lastName.isBlank() )
        {
            attributes.add( Map.of( "attribute", LAST_NAME_ATTRIBUTE, "value", lastName ) );
        }

        if ( patient.getGender() == Enumerations.AdministrativeGender.MALE )
        {
            attributes.add( Map.of( "attribute", GENDER_ATTRIBUTE, "value", "Male" ) );
        }
        else if ( patient.getGender() == Enumerations.AdministrativeGender.FEMALE )
        {
            attributes.add( Map.of( "attribute", GENDER_ATTRIBUTE, "value", "Female" ) );
        }

        return attributes;
    }

    @SuppressWarnings( "unchecked" )
    private String extractNewTrackedEntityId( Map<String, Object> response )
    {
        try
        {
            Map<String, Object> bundleReport = (Map<String, Object>) response.get( "bundleReport" );
            Map<String, Object> typeReportMap = (Map<String, Object>) bundleReport.get( "typeReportMap" );
            Map<String, Object> trackedEntityReport = (Map<String, Object>) typeReportMap.get( "TRACKED_ENTITY" );
            List<Map<String, Object>> objectReports =
                (List<Map<String, Object>>) trackedEntityReport.get( "objectReports" );
            return (String) objectReports.get( 0 ).get( "uid" );
        }
        catch ( RuntimeException e )
        {
            // Malformed/unexpected response shape -- treat as a failed create rather than
            // propagating a ClassCastException/NPE out of a single item in the split.
            return null;
        }
    }
}
