package org.hisp.dhis.fhir.converters;

import lombok.RequiredArgsConstructor;
import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.VaccineConfig;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.domain.ImmunizationAndPatient;
import org.hisp.dhis.fhir.domain.SyncLog;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// A plain Spring bean, invoked directly from the route via .bean(...) rather than
// registered as a Camel type converter -- a bean-registered @Converter method from
// Dhis2Resource (a HashMap subtype) to Immunization was never actually matched by
// Camel's converter lookup here, so this sidesteps that registry entirely.
@Component
@RequiredArgsConstructor
public class VaccinationEventToImmunizationConverter
{
    private static final String SYNC_ORIGIN_TAG_SYSTEM = "https://dhis2.org/fhir/sync-origin";
    private static final String SYNC_ORIGIN_TAG_CODE = "dhis2-sync";

    private final DhisProperties dhisProperties;

    private final SyncLogRepository syncLogRepository;

    // One DHIS2 event can carry more than one vaccine dose (e.g. the Birth stage event
    // records both BCG and OPV), so this returns a list rather than a single Immunization --
    // ImmunizationBundleAggregationStrategy flattens it. Never returns null; an event with
    // no doses given (or no data values at all) just produces an empty list.
    @SuppressWarnings( "unchecked" )
    public List<ImmunizationAndPatient> eventToImmunization( Dhis2Resource event )
    {
        String eventId = (String) event.get( "event" );
        String programStage = (String) event.get( "programStage" );
        String trackedEntityId = (String) event.get( "trackedEntity" );
        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) event.get( "dataValues" );

        List<ImmunizationAndPatient> results = new ArrayList<>();
        if ( dataValues == null )
        {
            return results;
        }

        for ( VaccineConfig vaccine : VaccineCatalog.forProgramStage( programStage ) )
        {
            String value = dataValues.stream()
                .filter( dv -> vaccine.getDataElementId().equals( dv.get( "dataElement" ) ) )
                .map( dv -> (String) dv.get( "value" ) )
                .findFirst()
                .orElse( null );

            if ( !vaccine.isGiven( value ) )
            {
                syncLogRepository.save( new SyncLog( "dhis2-to-fhir", eventId,
                    "skipped-not-" + vaccine.getCvxDisplay(), null ) );
                continue;
            }

            results.add( buildImmunizationAndPatient( eventId, trackedEntityId,
                (String) event.get( "occurredAt" ), vaccine ) );
        }

        return results;
    }

    private ImmunizationAndPatient buildImmunizationAndPatient( String eventId, String trackedEntityId,
        String occurredAt, VaccineConfig vaccine )
    {
        // One event can produce several Immunizations (one per vaccine), so each needs its
        // own FHIR id -- the bare event id would collide across them.
        String immunizationId = eventId + "-" + vaccine.getDataElementId();

        Patient patient = new Patient();
        patient.setId( trackedEntityId );
        patient.getMeta().addTag( SYNC_ORIGIN_TAG_SYSTEM, SYNC_ORIGIN_TAG_CODE, "Created by DHIS2-to-FHIR sync" );
        patient.getIdentifier().add(
            new Identifier().setSystem( dhisProperties.getBaseUrl() + "/api/trackedEntityInstances" )
                .setValue( trackedEntityId ) );

        Immunization immunization = new Immunization();
        immunization.setId( immunizationId );
        immunization.getMeta().addTag( SYNC_ORIGIN_TAG_SYSTEM, SYNC_ORIGIN_TAG_CODE, "Created by DHIS2-to-FHIR sync" );
        immunization.setStatus( Immunization.ImmunizationStatus.COMPLETED );

        immunization.setVaccineCode(
            new CodeableConcept().addCoding(
                new Coding( "http://hl7.org/fhir/sid/cvx", vaccine.getCvxCode(), vaccine.getCvxDisplay() ) ) );

        // Matches the urn:uuid fullUrl ImmunizationBundleAggregationStrategy gives the
        // Patient entry -- a plain "Patient/<id>" reference doesn't resolve within the same
        // transaction bundle unless the Patient entry happens to be created under that exact
        // literal id, which a conditional create doesn't guarantee (HAPI-1094 otherwise).
        immunization.setPatient( new Reference( "urn:uuid:patient-" + trackedEntityId ) );
        immunization.setOccurrence( new DateTimeType( occurredAt ) );

        immunization.getIdentifier().add(
            new Identifier().setSystem( dhisProperties.getBaseUrl() + "/api/tracker/events" )
                .setValue( immunizationId ) );

        syncLogRepository.save( new SyncLog( "dhis2-to-fhir", eventId, "created",
            "Immunization/" + immunizationId + " (" + vaccine.getCvxDisplay() + "), Patient/" + trackedEntityId ) );

        return new ImmunizationAndPatient( immunization, patient );
    }
}
