package org.hisp.dhis.fhir.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.hisp.dhis.api.model.v2_39_1.OrganisationUnit;
import org.hisp.dhis.fhir.aggregate.OrganizationAndLocationBundleAggregationStrategy;
import org.hisp.dhis.fhir.config.ScheduleGate;
import org.hisp.dhis.fhir.config.SyncTypes;
import org.hisp.dhis.fhir.config.properties.FhirProperties;
import org.hisp.dhis.fhir.domain.OrganizationAndLocation;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrgUnitToFhirBundleRoute extends RouteBuilder
{
    private final FhirProperties fhirProperties;

    private final ScheduleGate scheduleGate;

    @Override
    public void configure()
    {
        // Ticks every 5 minutes but mostly no-ops -- ScheduleGate decides whether this
        // type is actually due (AUTOMATIC + interval elapsed) or MANUAL (never auto-runs).
        // See ScheduleGate's own comment on why this polling approach beats trying to
        // rebuild the timer route itself with a reconfigured period.
        from( "timer://ouSyncTick?period=300000" ).routeId( "dhis2-ou-to-fhir-bundle-tick" )
            .filter( exchange -> scheduleGate.shouldRunNow( SyncTypes.ORG_UNITS_TO_FHIR ) )
            .to( "direct:orgUnitsToFhirSync" );

        from( "direct:orgUnitsToFhirSync" ).routeId( "dhis2-ou-to-fhir-bundle" )
            .to("dhis2://get/collection?path=organisationUnits&arrayName=organisationUnits&fields=id,code,name,description,parent[id]&client=#dhis2Client")
            .log( "Converting DHIS2 organisation units to FHIR organizations and locations..." )
            .split().body().aggregationStrategy( new OrganizationAndLocationBundleAggregationStrategy() )
                .convertBodyTo( OrganisationUnit.class )
                .convertBodyTo( OrganizationAndLocation.class )
            .end()
            .marshal().fhirJson( fhirProperties.getFhirVersion().name(), true )
            .to( "fhir://transaction/withBundle?client=#fhirClient" )
            .log( "Saved organizations and locations FHIR bundle" );
    }
}
