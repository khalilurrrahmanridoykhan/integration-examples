package org.hisp.dhis.fhir.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// DHIS2 demo "Child Programme" (IpHINAT79UW) vaccine data elements this service knows how
// to sync, across both program stages that carry vaccination data. CVX codes are a
// reasonable best-effort mapping for a demo integration, not a clinically-verified table --
// IPT (isoniazid prophylaxis) is deliberately excluded, since it's a drug, not a vaccine,
// and doesn't belong as a FHIR Immunization.
public final class VaccineCatalog
{
    public static final String PROGRAM = "IpHINAT79UW";

    public static final String BIRTH_STAGE = "A03MvHHogjR";
    public static final String BABY_POSTNATAL_STAGE = "ZzYYXq4fJie";

    public static final List<VaccineConfig> VACCINES = List.of(
        new VaccineConfig( "bx6fsa0t90x", BIRTH_STAGE, "19", "BCG", true ),
        new VaccineConfig( "ebaJjqltK5N", BIRTH_STAGE, "02", "OPV", false ),
        new VaccineConfig( "FqlgKAG8HOu", BABY_POSTNATAL_STAGE, "05", "measles", true ),
        new VaccineConfig( "rxBfISxXS2U", BABY_POSTNATAL_STAGE, "37", "yellow fever", true ),
        new VaccineConfig( "vTUhAUZFoys", BABY_POSTNATAL_STAGE, "110", "DTaP-Hep B-IPV (Penta)", false ),
        new VaccineConfig( "pOe0ogW4OWd", BABY_POSTNATAL_STAGE, "107", "DTaP, unspecified formulation", false )
    );

    private VaccineCatalog()
    {
    }

    public static Set<String> programStages()
    {
        return VACCINES.stream().map( VaccineConfig::getProgramStageId ).collect( Collectors.toSet() );
    }

    public static List<VaccineConfig> forProgramStage( String programStageId )
    {
        return VACCINES.stream()
            .filter( v -> v.getProgramStageId().equals( programStageId ) )
            .collect( Collectors.toList() );
    }

    public static Optional<VaccineConfig> byCvxCode( String cvxCode )
    {
        return VACCINES.stream().filter( v -> v.getCvxCode().equals( cvxCode ) ).findFirst();
    }
}
