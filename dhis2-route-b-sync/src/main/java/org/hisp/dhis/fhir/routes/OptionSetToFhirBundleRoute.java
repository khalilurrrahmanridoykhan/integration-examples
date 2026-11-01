package org.hisp.dhis.fhir.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.hisp.dhis.api.model.v2_39_1.OptionSet;
import org.hisp.dhis.fhir.config.properties.FhirProperties;
import org.hisp.dhis.fhir.aggregate.CodeSystemAndValueSetBundleAggregationStrategy;
import org.hisp.dhis.fhir.domain.CodeSystemAndValueSet;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OptionSetToFhirBundleRoute extends RouteBuilder
{
    private final FhirProperties fhirProperties;

    @Override
    public void configure()
    {
        // timer and direct both feed the same logic, so a manual REST trigger
        // (SyncTriggerController -> direct:optionSetsToFhirSync) runs the identical route.
        from( "timer://osSync?period=3600000" ).routeId( "dhis2-os-to-fhir-bundle-timer" )
            .to( "direct:optionSetsToFhirSync" );

        from( "direct:optionSetsToFhirSync" ).routeId( "dhis2-os-to-fhir-bundle" )
            .to("dhis2://get/collection?path=optionSets&arrayName=optionSets&fields=id,code,name,description,version,options[id,code,name]&filter=id:eq:HB33RvLvVZe&client=#dhis2Client")
            .log( "Converting DHIS2 options sets to FHIR code systems and value sets..." )
            .split().body().aggregationStrategy( new CodeSystemAndValueSetBundleAggregationStrategy() )
                .convertBodyTo( OptionSet.class )
                .convertBodyTo( CodeSystemAndValueSet.class )
            .end()
            .marshal().fhirJson( fhirProperties.getFhirVersion().name(), true )
            .to( "fhir://transaction/withBundle?client=#fhirClient" )
            .log( "Saved code systems and value sets FHIR bundle" );
    }
}
