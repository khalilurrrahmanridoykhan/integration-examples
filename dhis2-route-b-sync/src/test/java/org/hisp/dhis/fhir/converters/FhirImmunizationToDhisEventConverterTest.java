package org.hisp.dhis.fhir.converters;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IRead;
import ca.uhn.fhir.rest.gclient.IReadExecutable;
import ca.uhn.fhir.rest.gclient.IReadTyped;
import ca.uhn.fhir.rest.gclient.IUpdate;
import ca.uhn.fhir.rest.gclient.IUpdateTyped;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.domain.PreviewEntry;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.Dhis2Response;
import org.hisp.dhis.integration.sdk.api.operation.GetOperation;
import org.hisp.dhis.integration.sdk.api.operation.PostOperation;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class FhirImmunizationToDhisEventConverterTest
{
    // Same demo values as DhisProperties' own defaults -- see application.yml for how an
    // operator would override these to point at a different DHIS2 program/org unit.
    private static final String PROGRAM = "IpHINAT79UW";
    private static final String BABY_POSTNATAL_STAGE = "ZzYYXq4fJie";

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
        DhisProperties dhisProperties = new DhisProperties();
        VaccineCatalog vaccineCatalog = new VaccineCatalog( dhisProperties );
        converter = new FhirImmunizationToDhisEventConverter( fhirClient, dhis2Client, dhisProperties, vaccineCatalog,
            syncLogRepository );
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

    @SuppressWarnings( "unchecked" )
    private void stubTrackerImport( Map<String, Object> response )
    {
        PostOperation postOperation = org.mockito.Mockito.mock( PostOperation.class );
        Dhis2Response dhis2Response = org.mockito.Mockito.mock( Dhis2Response.class );

        when( dhis2Client.post( "tracker" ) ).thenReturn( postOperation );
        when( postOperation.withResource( any() ) ).thenReturn( postOperation );
        when( postOperation.withParameter( "async", "false" ) ).thenReturn( postOperation );
        when( postOperation.transfer() ).thenReturn( dhis2Response );
        when( dhis2Response.returnAs( Map.class ) ).thenReturn( response );
    }

    private void stubPatientUpdate()
    {
        IUpdate update = org.mockito.Mockito.mock( IUpdate.class );
        IUpdateTyped updateTyped = org.mockito.Mockito.mock( IUpdateTyped.class );

        when( fhirClient.update() ).thenReturn( update );
        when( update.resource( any( Patient.class ) ) ).thenReturn( updateTyped );
        when( updateTyped.execute() ).thenReturn( null );
    }

    // A map shaped like DHIS2's real nested tracker-import response (confirmed live), just
    // the one field this converter actually reads out of it.
    private Map<String, Object> trackerImportResponseWithNewTrackedEntityId( String newTrackedEntityId )
    {
        return Map.of( "bundleReport", Map.of( "typeReportMap", Map.of( "TRACKED_ENTITY",
            Map.of( "objectReports", List.of( Map.of( "uid", newTrackedEntityId ) ) ) ) ) );
    }

    @Test
    void patientWithNoDhis2Identifier_createsNewTrackedEntityAndWritesIdBackToFhir()
    {
        Patient patient = new Patient();
        patient.getNameFirstRep().addGiven( "Jane" ).setFamily( "Doe" );
        patient.setGender( org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE );
        stubPatientRead( patient );
        stubTrackerImport( trackerImportResponseWithNewTrackedEntityId( "newTe123" ) );
        stubPatientUpdate();

        Map<String, Object> result = converter.immunizationToDhisEventPayload( measlesImmunization( false ) );

        // The route has nothing left to POST -- the nested tracker import above already
        // created the trackedEntity, enrollment, and event in one atomic call.
        assertNull( result );
        assertEquals( "newTe123", patient.getIdentifierFirstRep().getValue() );
        verify( fhirClient ).update();
    }

    @Test
    void trackerImportRespondsWithAnUnexpectedShape_skipsRatherThanThrowing()
    {
        stubPatientRead( new Patient() );
        // Not the real DHIS2 response shape (e.g. the "Tracker job added" async response,
        // or a validation error) -- must not NPE/ClassCastException out of the split.
        stubTrackerImport( Map.of( "status", "ERROR" ) );

        Map<String, Object> result = converter.immunizationToDhisEventPayload( measlesImmunization( false ) );

        assertNull( result );
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
        assertEquals( PROGRAM, event.get( "program" ) );
        assertEquals( BABY_POSTNATAL_STAGE, event.get( "programStage" ) );

        List<Map<String, Object>> dataValues = (List<Map<String, Object>>) event.get( "dataValues" );
        assertEquals( 1, dataValues.size() );
        assertEquals( "FqlgKAG8HOu", dataValues.get( 0 ).get( "dataElement" ) );
        assertEquals( "true", dataValues.get( 0 ).get( "value" ) );
    }

    // The critical safety property of preview: for a brand-new patient, the real method
    // (immunizationToDhisEventPayload) creates the tracked entity as a side effect of being
    // called at all, via a direct dhis2Client.post(...) + fhirClient.update(...) -- NOT
    // deferred to the route's own write step like every other branch. previewImmunization
    // must describe that outcome without ever making either call.
    @Test
    void previewImmunization_newPatient_describesWithoutWritingAnything()
    {
        stubPatientRead( new Patient() );

        PreviewEntry entry = converter.previewImmunization( measlesImmunization( false ) );

        assertEquals( "would-create-new-tracked-entity", entry.getAction() );
        verify( dhis2Client, never() ).post( any() );
        verify( fhirClient, never() ).update();
    }

    @Test
    void previewImmunization_existingPatientWithEvent_describesWouldUpdateWithoutPosting()
    {
        Patient patient = new Patient();
        patient.getIdentifier().add( new Identifier().setValue( "te1" ) );
        stubPatientRead( patient );
        stubExistingDhis2Events( List.of( Map.of( "event", "existingEvt" ) ) );

        PreviewEntry entry = converter.previewImmunization( measlesImmunization( false ) );

        assertEquals( "would-update", entry.getAction() );
        verify( dhis2Client, never() ).post( any() );
    }

    @Test
    void previewImmunization_noExistingEvent_skipsWithoutPosting()
    {
        Patient patient = new Patient();
        patient.getIdentifier().add( new Identifier().setValue( "te1" ) );
        stubPatientRead( patient );
        stubExistingDhis2Events( List.of() );

        PreviewEntry entry = converter.previewImmunization( measlesImmunization( false ) );

        assertEquals( "skipped-no-existing-event", entry.getAction() );
        verify( dhis2Client, never() ).post( any() );
    }

    @Test
    void previewImmunization_ownOutputTag_skipsWithoutTouchingFhirOrDhis2()
    {
        PreviewEntry entry = converter.previewImmunization( measlesImmunization( true ) );

        assertEquals( "skipped-own-output", entry.getAction() );
        verifyNoInteractions( fhirClient, dhis2Client );
    }
}
