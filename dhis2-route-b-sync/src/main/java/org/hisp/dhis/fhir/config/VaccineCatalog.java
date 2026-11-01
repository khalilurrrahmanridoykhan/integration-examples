package org.hisp.dhis.fhir.config;

import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Which DHIS2 program this service syncs Immunizations against, and which of that
// program's data elements represent vaccine doses -- built once at startup from
// DhisProperties (backed by application.yml), so repointing this service at a different
// DHIS2 program/vaccine set is a config change, not a code change. CVX codes are a
// reasonable best-effort mapping for a demo integration, not a clinically-verified table.
@Component
public class VaccineCatalog
{
    private final DhisProperties dhisProperties;

    private final List<VaccineConfig> vaccines;

    public VaccineCatalog( DhisProperties dhisProperties )
    {
        this.dhisProperties = dhisProperties;
        this.vaccines = dhisProperties.getVaccines().stream()
            .map( v -> new VaccineConfig( v.getDataElementId(), v.getProgramStage(), v.getCvxCode(),
                v.getCvxDisplay(), v.isBooleanStyle() ) )
            .collect( Collectors.toUnmodifiableList() );
    }

    public String getProgram()
    {
        return dhisProperties.getProgram();
    }

    public String getOrgUnit()
    {
        return dhisProperties.getOrgUnit();
    }

    public Set<String> programStages()
    {
        return vaccines.stream().map( VaccineConfig::getProgramStageId ).collect( Collectors.toSet() );
    }

    public List<VaccineConfig> forProgramStage( String programStageId )
    {
        return vaccines.stream()
            .filter( v -> v.getProgramStageId().equals( programStageId ) )
            .collect( Collectors.toList() );
    }

    public Optional<VaccineConfig> byCvxCode( String cvxCode )
    {
        return vaccines.stream().filter( v -> v.getCvxCode().equals( cvxCode ) ).findFirst();
    }
}
