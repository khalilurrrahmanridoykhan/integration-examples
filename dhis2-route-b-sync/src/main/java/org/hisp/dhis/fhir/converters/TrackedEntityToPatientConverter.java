package org.hisp.dhis.fhir.converters;

import lombok.RequiredArgsConstructor;
import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.apache.commons.lang3.StringUtils;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.domain.SyncLog;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// Ported to the new Tracker API (tracker/trackedEntities) -- the legacy
// trackedEntityInstances endpoint this previously read is gone from current DHIS2.
// A plain Spring bean invoked via .bean(...), not a Camel type converter -- same reason
// as the Immunization routes: a registered @Converter keyed on Dhis2Resource was never
// matched by Camel's converter lookup here.
@Component
@RequiredArgsConstructor
public class TrackedEntityToPatientConverter
{
    private static final String GENDER_ATTRIBUTE = "cejWyOfXge6";
    private static final String FIRST_NAME_ATTRIBUTE = "w75KJ2mc4zz";
    private static final String LAST_NAME_ATTRIBUTE = "zDhUuAYrxNC";

    private final DhisProperties dhisProperties;

    private final SyncLogRepository syncLogRepository;

    @SuppressWarnings( "unchecked" )
    public Patient trackedEntityResourceToPatient( Dhis2Resource trackedEntity )
    {
        String trackedEntityId = (String) trackedEntity.get( "trackedEntity" );
        String orgUnit = (String) trackedEntity.get( "orgUnit" );
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) trackedEntity.get( "attributes" );

        String namespace = dhisProperties.getBaseUrl() + "/trackedEntityInstances";

        Patient patient = new Patient();
        patient.setId( trackedEntityId );

        patient.getIdentifier().add(
            new Identifier().setSystem( namespace ).setValue( trackedEntityId ) );

        patient.setManagingOrganization( new Reference( "Organization?identifier=" + orgUnit ) );

        String gender = getAttributeValue( attributes, GENDER_ATTRIBUTE );
        String firstName = getAttributeValue( attributes, FIRST_NAME_ATTRIBUTE );
        String lastName = getAttributeValue( attributes, LAST_NAME_ATTRIBUTE );

        patient.setGender( getGender( gender ) );
        patient.getName().add( new HumanName().addGiven( firstName ).setFamily( lastName ) );

        syncLogRepository.save( new SyncLog( "dhis2-to-fhir", trackedEntityId, "created",
            "Patient/" + trackedEntityId ) );

        return patient;
    }

    private Enumerations.AdministrativeGender getGender( String gender )
    {
        if ( gender == null )
        {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }

        switch ( gender )
        {
        case "Female":
            return Enumerations.AdministrativeGender.FEMALE;
        case "Male":
            return Enumerations.AdministrativeGender.MALE;
        default:
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
    }

    private String getAttributeValue( List<Map<String, Object>> attributes, String id )
    {
        if ( attributes == null )
        {
            return null;
        }
        for ( Map<String, Object> attribute : attributes )
        {
            if ( id.equals( attribute.get( "attribute" ) ) )
            {
                return StringUtils.trimToEmpty( (String) attribute.get( "value" ) );
            }
        }
        return null;
    }
}
