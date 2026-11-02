package org.hisp.dhis.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.hisp.dhis.api.model.v2_39_1.OptionSet;
import org.hisp.dhis.api.model.v2_39_1.OrganisationUnit;
import org.hisp.dhis.fhir.config.VaccineCatalog;
import org.hisp.dhis.fhir.config.properties.DhisProperties;
import org.hisp.dhis.fhir.converters.FhirImmunizationToDhisEventConverter;
import org.hisp.dhis.fhir.converters.OptionSetToCodeSystemAndValueSetConverter;
import org.hisp.dhis.fhir.converters.OrgUnitToOrganizationAndLocationConverter;
import org.hisp.dhis.fhir.converters.TrackedEntityToPatientConverter;
import org.hisp.dhis.fhir.converters.VaccinationEventToImmunizationConverter;
import org.hisp.dhis.fhir.domain.PreviewEntry;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Immunization;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Same five sync types as SyncTriggerController, but read-only: fetches and converts
// exactly like the real route, minus the final write step (and, for Immunization
// FHIR->DHIS2, minus the new-tracked-entity branch's own direct DHIS2/FHIR writes --
// see FhirImmunizationToDhisEventConverter.previewImmunization's own comment). Calls the
// DHIS2 SDK directly rather than going through a Camel route, mirroring the pattern
// FhirImmunizationToDhisEventConverter already uses for synchronous reads.
@RestController
@RequestMapping( "/api/preview" )
@RequiredArgsConstructor
public class PreviewController
{
    private final Dhis2Client dhis2Client;

    private final IGenericClient fhirClient;

    private final DhisProperties dhisProperties;

    private final VaccineCatalog vaccineCatalog;

    private final VaccinationEventToImmunizationConverter vaccinationEventToImmunizationConverter;

    private final FhirImmunizationToDhisEventConverter fhirImmunizationToDhisEventConverter;

    private final OrgUnitToOrganizationAndLocationConverter orgUnitToOrganizationAndLocationConverter;

    private final OptionSetToCodeSystemAndValueSetConverter optionSetToCodeSystemAndValueSetConverter;

    private final TrackedEntityToPatientConverter trackedEntityToPatientConverter;

    @GetMapping( "/{type}" )
    public ResponseEntity<?> preview( @PathVariable String type )
    {
        switch ( type )
        {
        case "immunization-to-fhir":
            return ResponseEntity.ok( previewImmunizationToFhir() );
        case "immunization-to-dhis2":
            return ResponseEntity.ok( previewImmunizationToDhis2() );
        case "org-units-to-fhir":
            return ResponseEntity.ok( previewOrgUnits() );
        case "option-sets-to-fhir":
            return ResponseEntity.ok( previewOptionSets() );
        case "tracked-entities-to-fhir":
            return ResponseEntity.ok( previewTrackedEntities() );
        default:
            return ResponseEntity.badRequest().body( Map.of( "error", "Unknown sync type '" + type + "'" ) );
        }
    }

    private List<PreviewEntry> previewImmunizationToFhir()
    {
        List<PreviewEntry> entries = new ArrayList<>();
        for ( String programStage : vaccineCatalog.programStages() )
        {
            Iterable<Dhis2Resource> events = dhis2Client.get( "tracker/events" )
                .withFields( "event,programStage,trackedEntity,occurredAt,dataValues" )
                .withoutPaging()
                .withParameter( "program", vaccineCatalog.getProgram() )
                .withParameter( "programStage", programStage )
                .withParameter( "orgUnit", dhisProperties.getOrgUnit() )
                .transfer()
                .returnAs( Dhis2Resource.class, "events" );

            for ( Dhis2Resource event : events )
            {
                entries.addAll( vaccinationEventToImmunizationConverter.previewEvent( event ) );
            }
        }
        return entries;
    }

    private List<PreviewEntry> previewImmunizationToDhis2()
    {
        // Same 200-cap as the real route -- see FhirImmunizationToDhisEventRoute's own
        // comment on HAPI's default page size silently truncating results otherwise.
        Bundle bundle = fhirClient.search().forResource( Immunization.class )
            .count( 200 )
            .returnBundle( Bundle.class )
            .execute();

        return bundle.getEntry().stream()
            .map( entry -> (Immunization) entry.getResource() )
            .map( fhirImmunizationToDhisEventConverter::previewImmunization )
            .collect( Collectors.toList() );
    }

    private List<PreviewEntry> previewOrgUnits()
    {
        Iterable<OrganisationUnit> orgUnits = dhis2Client.get( "organisationUnits" )
            .withFields( "id,code,name,description,parent[id]" )
            .withoutPaging()
            .transfer()
            .returnAs( OrganisationUnit.class, "organisationUnits" );

        List<PreviewEntry> entries = new ArrayList<>();
        for ( OrganisationUnit organisationUnit : orgUnits )
        {
            entries.add( orgUnitToOrganizationAndLocationConverter.previewOrgUnit( organisationUnit ) );
        }
        return entries;
    }

    private List<PreviewEntry> previewOptionSets()
    {
        // withFilter (not withParameter("filter", ...)) -- GetOperation.withParameter
        // narrows the fluent return type to the generic ParameterizedOperation, which has
        // no withoutPaging() of its own, so the filter has to go on before that call.
        Iterable<OptionSet> optionSets = dhis2Client.get( "optionSets" )
            .withFields( "id,code,name,description,version,options[id,code,name]" )
            .withFilter( "id:eq:HB33RvLvVZe" )
            .withoutPaging()
            .transfer()
            .returnAs( OptionSet.class, "optionSets" );

        List<PreviewEntry> entries = new ArrayList<>();
        for ( OptionSet optionSet : optionSets )
        {
            entries.add( optionSetToCodeSystemAndValueSetConverter.previewOptionSet( optionSet ) );
        }
        return entries;
    }

    private List<PreviewEntry> previewTrackedEntities()
    {
        // orgUnits (plural) -- see TrackedEntityToFhirBundleRoute's own comment on why this
        // endpoint needs the plural param name, unlike tracker/events above.
        Iterable<Dhis2Resource> trackedEntities = dhis2Client.get( "tracker/trackedEntities" )
            .withoutPaging()
            .withParameter( "orgUnits", dhisProperties.getOrgUnit() )
            .withParameter( "program", vaccineCatalog.getProgram() )
            .transfer()
            .returnAs( Dhis2Resource.class, "trackedEntities" );

        List<PreviewEntry> entries = new ArrayList<>();
        for ( Dhis2Resource trackedEntity : trackedEntities )
        {
            entries.add( trackedEntityToPatientConverter.previewTrackedEntity( trackedEntity ) );
        }
        return entries;
    }
}
