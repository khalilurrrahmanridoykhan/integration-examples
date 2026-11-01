package org.hisp.dhis.fhir.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaccineCatalogTest
{
    @Test
    void forProgramStage_birthStage_hasBcgAndOpvOnly()
    {
        List<VaccineConfig> birthStageVaccines = VaccineCatalog.forProgramStage( VaccineCatalog.BIRTH_STAGE );

        assertEquals( 2, birthStageVaccines.size() );
        assertTrue( birthStageVaccines.stream().anyMatch( v -> v.getCvxCode().equals( "19" ) ) );
        assertTrue( birthStageVaccines.stream().anyMatch( v -> v.getCvxCode().equals( "02" ) ) );
    }

    @Test
    void forProgramStage_babyPostnatalStage_hasFourVaccines()
    {
        List<VaccineConfig> babyPostnatalVaccines =
            VaccineCatalog.forProgramStage( VaccineCatalog.BABY_POSTNATAL_STAGE );

        assertEquals( 4, babyPostnatalVaccines.size() );
    }

    @Test
    void forProgramStage_unknownStage_returnsEmpty()
    {
        assertEquals( List.of(), VaccineCatalog.forProgramStage( "not-a-real-stage" ) );
    }

    @Test
    void byCvxCode_findsBcgByItsCvxCode()
    {
        Optional<VaccineConfig> bcg = VaccineCatalog.byCvxCode( "19" );

        assertTrue( bcg.isPresent() );
        assertEquals( "BCG", bcg.get().getCvxDisplay() );
        assertEquals( VaccineCatalog.BIRTH_STAGE, bcg.get().getProgramStageId() );
    }

    @Test
    void byCvxCode_unknownCode_returnsEmpty()
    {
        assertEquals( Optional.empty(), VaccineCatalog.byCvxCode( "999" ) );
    }
}
