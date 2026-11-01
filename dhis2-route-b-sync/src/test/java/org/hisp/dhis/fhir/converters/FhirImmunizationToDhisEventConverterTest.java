package org.hisp.dhis.fhir.converters;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IRead;
import ca.uhn.fhir.rest.gclient.IReadExecutable;
import ca.uhn.fhir.rest.gclient.IReadTyped;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.operation.GetOperation;
import org.hisp.dhis.integration.sdk.api.operation.SimpleCollectOperation;
import org.hisp.dhis.integration.sdk.internal.SimpleIterableDhis2Response;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class FhirImmunizationToDhisEventConverterTest
{
    @Mock
    private IGenericClient fhirClient;

    @Mock
    private Dhis2Client dhis2Client;

    @Mock
    private SyncLogRepository syncLogRepository;

    private FhirImmunizationToDhisEventConverter converter;

    @BeforeEach
    void setUp()
    {
        converter = new FhirImmunizationToDhisEventConverter( fhirClient, dhis2Client, syncLogRepository );
    }

    private Immunization measlesImmunization( boolean ownOutputTag )
    {
        Immunization immunization = new Immunization();
        immunization.setId( "immz1" );
        if ( ownOutputTag )
        {
            immunization.getMeta().addTag( "https://dhis2.org/fhir/sync-origin", "dhis2-sync", "test" );
        }
        immunization.setVaccineCode(
            new CodeableConcept().addCoding( new Coding( "http://hl7.org/fhir/sid/cvx", "05", "measles" ) ) );
        immunization.setPatient( new Reference( "Patient/testPatient" ) );
        immunization.setOccurrence( new DateTimeType( "2024-01-01" ) );
        return immunization;
    }

    @Test
    void ownOutputTag_skipsWithoutTouchingFhirOrDhis2()
    {
        Map<String, Object> result = converter.immunizationToDhisEventPayload( measlesImmunization( true ) );

        assertNull( result );
        verifyNoInteractions( fhirClient, dhis2Client );
    }

    @Test
    void unknownCvxCode_skipsWithoutLookingUpPatient()
    {
        Immunization immunization = new Immunization();
        immunization.setId( "immz1" );
        immunization.setVaccineCode(
            new CodeableConcept().addCoding( new Coding( "http://hl7.org/fhir/sid/cvx", "999", "not-a-real-vaccine" ) ) );

        Map<String, Object> result = converter.immunizationToDhisEventPayload( immunization );

        assertNull( result );
        verifyNoInteractions( fhirClient, dhis2Client );
    }

    @SuppressWarnings( "unchecked" )
    private void stubPatientRead( Patient patientToReturn )
    {
        IRead read = org.mockito.Mockito.mock( IRead.class );
        IReadTyped<Patient> readTyped = org.mockito.Mockito.mock( IReadTyped.class );
        IReadExecutable<Patient> readExecutable = org.mockito.Mockito.mock( IReadExecutable.class );

        when( fhirClient.read() ).thenReturn( read );
        when( read.resource( Patient.class ) ).thenReturn( readTyped );
        when( readTyped.withId( any( String.class ) ) ).thenReturn( readExecutable );
        when( readExecutable.execute() ).thenReturn( patientToReturn );
    }

    @Test
    void patientWithNoDhis2Identifier_skipsAsNotMapped()
    {
        stubPatientRead( new Patient() );

        Map<String, Object> result = converter.immunizationToDhisEventPayload( measlesImmunization( false ) );

        assertNull( result );
        verifyNoInteractions( dhis2Client );
    }

    @SuppressWarnings( { "unchecked", "rawtypes" } )
    private void stubExistingDhis2Events( List<Map> events )
    {
        GetOperation getOperation = org.mockito.Mockito.mock( GetOperation.class );
        SimpleCollectOperation collectOperation = org.mockito.Mockito.mock( SimpleCollectOperation.class );
        SimpleIterableDhis2Response response = org.mockito.Mockito.mock( SimpleIterableDhis2Response.class );

        when( dhis2Client.get( "tracker/events" ) ).thenReturn( getOperation );
        when( getOperation.withFields( any( String.class ) ) ).thenReturn( getOperation );
        when( getOperation.withoutPaging() ).thenReturn( collectOperation );
        when( collectOperation.withParameter( any( String.class ), any( String.class ) ) ).thenReturn( collectOperation );
        when( collectOperation.transfer() ).thenReturn( response );
        when( response.returnAs( Map.class, "events" ) ).thenReturn( events );
    }

    @Test
    void noExistingDhis2Event_skipsSinceBirthAndPostnatalStagesAreNonRepeatable()
    {
        Patient patient = new Patient();
        patient.getIdentifier().add( new Identifier().setValue( "te1" ) );
        stubPatientRead( patient );
        stubExistingDhis2Events( List.of() );

        Map<String, Object> result = converter.immunizationToDhisEventPayload( measlesImmunization( false ) );

        assertNull( result );
    }

    @Test
    @SuppressWarnings( "unchecked" )
    void knownVaccineWithExistingEvent_buildsUpdatePayloadForTheCorrectProgramStage()
    {
        Patient patient = new Patient();
        patient.getIdentifier().add( new Identifier().setValue( "te1" ) );
        stubPatientRead( patient );
        stubExistingDhis2Events( List.of( Map.of( "event", "existingEvt", "enrollment", "enr1" ) ) );

        Map<String, Object> payload = converter.immunizationToDhisEventPayload( measlesImmunization( false ) );

        List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get( "events" );
        assertEquals( 1, events.size() );
        Map<String, Object> event = events.get( 0 );
        assertEquals( "existingEvt", event.get( "event" ) );
        assertEquals( "enr1", event.get( "enrollment" ) );
        assertEquals( "te1", event.get( "trackedEntity" ) );
        assertEquals( VaccineCatalog.PROGRAM, event.get( "program" ) );
        assertEquals( VaccineCatalog.BABY_POSTNATAL_STAGE, event.get( "programStage" ) );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) event.get( "dataValues" );
        assertEquals( 1, dataValues.size() );
        assertEquals( "FqlgKAG8HOu", dataValues.get( 0 ).get( "dataElement" ) );
        assertEquals( "true", dataValues.get( 0 ).get( "value" ) );
    }
}
