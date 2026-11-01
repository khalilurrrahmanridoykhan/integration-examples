package org.hisp.dhis.fhir.converters;

import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.domain.ImmunizationAndPatient;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith( MockitoExtension.class )
class VaccinationEventToImmunizationConverterTest
{
    @Mock
    private SyncLogRepository syncLogRepository;

    private VaccinationEventToImmunizationConverter converter;

    @BeforeEach
    void setUp()
    {
        DhisProperties dhisProperties = new DhisProperties();
        dhisProperties.setBaseUrl( "https://play.im.dhis2.org/dev/api" );
        dhisProperties.setUsername( "admin" );
        dhisProperties.setPassword( "district" );
        converter = new VaccinationEventToImmunizationConverter( dhisProperties, syncLogRepository );
    }

    private Dhis2Resource birthStageEvent( String bcgValue, String opvValue )
    {
        Dhis2Resource event = new Dhis2Resource();
        event.put( "event", "evt1" );
        event.put( "programStage", VaccineCatalog.BIRTH_STAGE );
        event.put( "trackedEntity", "te1" );
        event.put( "occurredAt", "2024-01-01T00:00:00.000" );
        event.put( "dataValues", List.of(
            Map.of( "dataElement", "bx6fsa0t90x", "value", bcgValue ),
            Map.of( "dataElement", "ebaJjqltK5N", "value", opvValue )
        ) );
        return event;
    }

    @Test
    void oneEventWithTwoVaccinesGiven_producesTwoImmunizations()
    {
        // The real bug this guards against: a single Birth-stage event recording both BCG
        // and OPV used to collide into one FHIR id/one Patient bundle entry (HAPI-2008).
        List<ImmunizationAndPatient> results = converter.eventToImmunization( birthStageEvent( "true", "1" ) );

        assertEquals( 2, results.size() );

        ImmunizationAndPatient bcgResult = results.get( 0 );
        assertEquals( "evt1-bx6fsa0t90x", bcgResult.getImmunization().getId() );
        assertEquals( "19", bcgResult.getImmunization().getVaccineCode().getCodingFirstRep().getCode() );
        assertEquals( "urn:uuid:patient-te1", bcgResult.getImmunization().getPatient().getReference() );
        assertEquals( "te1", bcgResult.getPatient().getId() );

        ImmunizationAndPatient opvResult = results.get( 1 );
        assertEquals( "evt1-ebaJjqltK5N", opvResult.getImmunization().getId() );
        assertEquals( "02", opvResult.getImmunization().getVaccineCode().getCodingFirstRep().getCode() );
    }

    @Test
    void neitherVaccineGiven_producesNoImmunizationsButLogsBothSkips()
    {
        List<ImmunizationAndPatient> results = converter.eventToImmunization( birthStageEvent( "false", "" ) );

        assertTrue( results.isEmpty() );
    }

    @Test
    void missingDataValues_producesNoImmunizationsAndNoLogEntries()
    {
        Dhis2Resource event = new Dhis2Resource();
        event.put( "event", "evt1" );
        event.put( "programStage", VaccineCatalog.BIRTH_STAGE );

        List<ImmunizationAndPatient> results = converter.eventToImmunization( event );

        assertTrue( results.isEmpty() );
        verifyNoInteractions( syncLogRepository );
    }

    @Test
    void eventFromUnconfiguredProgramStage_producesNoImmunizations()
    {
        Dhis2Resource event = new Dhis2Resource();
        event.put( "event", "evt1" );
        event.put( "programStage", "some-other-stage-not-in-the-catalog" );
        event.put( "dataValues", List.of( Map.of( "dataElement", "bx6fsa0t90x", "value", "true" ) ) );

        List<ImmunizationAndPatient> results = converter.eventToImmunization( event );

        assertTrue( results.isEmpty() );
    }
}
