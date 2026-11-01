package org.hisp.dhis.fhir.config;

import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaccineCatalogTest
{
    // Same demo values as DhisProperties' own defaults -- see application.yml for how an
    // operator would override these to point at a different DHIS2 program/vaccine set.
    private static final String BIRTH_STAGE = "A03MvHHogjR";
    private static final String BABY_POSTNATAL_STAGE = "ZzYYXq4fJie";

    private VaccineCatalog catalog;

    @BeforeEach
    void setUp()
    {
        catalog = new VaccineCatalog( new DhisProperties() );
    }

    @Test
    void forProgramStage_birthStage_hasBcgAndOpvOnly()
    {
        List<VaccineConfig> birthStageVaccines = catalog.forProgramStage( BIRTH_STAGE );

        assertEquals( 2, birthStageVaccines.size() );
        assertTrue( birthStageVaccines.stream().anyMatch( v -> v.getCvxCode().equals( "19" ) ) );
        assertTrue( birthStageVaccines.stream().anyMatch( v -> v.getCvxCode().equals( "02" ) ) );
    }

    @Test
    void forProgramStage_babyPostnatalStage_hasFourVaccines()
    {
        List<VaccineConfig> babyPostnatalVaccines = catalog.forProgramStage( BABY_POSTNATAL_STAGE );

        assertEquals( 4, babyPostnatalVaccines.size() );
    }

    @Test
    void forProgramStage_unknownStage_returnsEmpty()
    {
        assertEquals( List.of(), catalog.forProgramStage( "not-a-real-stage" ) );
    }

    @Test
    void byCvxCode_findsBcgByItsCvxCode()
    {
        Optional<VaccineConfig> bcg = catalog.byCvxCode( "19" );

        assertTrue( bcg.isPresent() );
        assertEquals( "BCG", bcg.get().getCvxDisplay() );
        assertEquals( BIRTH_STAGE, bcg.get().getProgramStageId() );
    }

    @Test
    void byCvxCode_unknownCode_returnsEmpty()
    {
        assertEquals( Optional.empty(), catalog.byCvxCode( "999" ) );
    }

    @Test
    void programStages_reflectsWhateverStagesAreConfigured_notAFixedCountInCode()
    {
        DhisProperties customProperties = new DhisProperties();
        customProperties.setVaccines( List.of(
            new DhisProperties.VaccineProperties( "de1", "stageOne", "19", "BCG", true ),
            new DhisProperties.VaccineProperties( "de2", "stageTwo", "02", "OPV", false ),
            new DhisProperties.VaccineProperties( "de3", "stageThree", "05", "measles", true )
        ) );

        assertEquals( 3, new VaccineCatalog( customProperties ).programStages().size() );
    }
}
