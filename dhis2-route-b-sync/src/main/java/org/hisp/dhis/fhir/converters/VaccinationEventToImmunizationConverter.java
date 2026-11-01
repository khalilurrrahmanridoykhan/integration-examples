package org.hisp.dhis.fhir.converters;

import lombok.RequiredArgsConstructor;
import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.domain.ImmunizationAndPatient;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

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
    // DHIS2 demo "Child Programme" (IpHINAT79UW), "Birth" stage (A03MvHHogjR) data element
    private static final String BCG_DOSE_DATA_ELEMENT = "bx6fsa0t90x";

    private final DhisProperties dhisProperties;

    @SuppressWarnings( "unchecked" )
    public ImmunizationAndPatient eventToImmunization( Dhis2Resource event )
    {
        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) event.get( "dataValues" );
        if ( dataValues == null )
        {
            return null;
        }

        boolean bcgGiven = dataValues.stream()
            .anyMatch( dv -> BCG_DOSE_DATA_ELEMENT.equals( dv.get( "dataElement" ) )
                && "true".equals( dv.get( "value" ) ) );

        if ( !bcgGiven )
        {
            return null;
        }

        String eventId = (String) event.get( "event" );
        String trackedEntityId = (String) event.get( "trackedEntity" );

        // Minimal Patient, created in the same bundle so Immunization.patient resolves --
        // HAPI rejects a transaction referencing a Patient that doesn't already exist.
        Patient patient = new Patient();
        patient.setId( trackedEntityId );
        patient.getIdentifier().add(
            new Identifier().setSystem( dhisProperties.getBaseUrl() + "/api/trackedEntityInstances" )
                .setValue( trackedEntityId ) );

        Immunization immunization = new Immunization();
        immunization.setId( eventId );
        immunization.setStatus( Immunization.ImmunizationStatus.COMPLETED );

        // https://hl7.org/fhir/R4/immunization.html -- CVX code 19 = BCG
        immunization.setVaccineCode(
            new CodeableConcept().addCoding(
                new Coding( "http://hl7.org/fhir/sid/cvx", "19", "BCG" ) ) );

        immunization.setPatient( new Reference( "Patient/" + trackedEntityId ) );
        immunization.setOccurrence( new DateTimeType( (String) event.get( "occurredAt" ) ) );

        immunization.getIdentifier().add(
            new Identifier().setSystem( dhisProperties.getBaseUrl() + "/api/tracker/events" )
                .setValue( eventId ) );

        return new ImmunizationAndPatient( immunization, patient );
    }
}
