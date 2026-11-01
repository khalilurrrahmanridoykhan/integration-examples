package org.hisp.dhis.fhir.aggregate;

import org.apache.camel.Exchange;
import org.hisp.dhis.fhir.domain.ImmunizationAndPatient;
import org.hl7.fhir.r4.model.Bundle;

import java.util.List;

public class ImmunizationBundleAggregationStrategy extends AbstractBundleAggregationStrategy
{
    @Override
    protected List<Bundle.BundleEntryComponent> getEntries( Exchange exchange )
    {
        ImmunizationAndPatient immunizationAndPatient = exchange.getIn().getBody( ImmunizationAndPatient.class );
        if ( immunizationAndPatient == null )
        {
            // non-BCG event, skipped by the converter -- nothing to add to the bundle
            return List.of();
        }

        Bundle.BundleEntryComponent patientEntry = new Bundle.BundleEntryComponent();
        patientEntry.setResource( immunizationAndPatient.getPatient() )
            .getRequest().setUrl( "Patient?identifier=" + immunizationAndPatient.getPatient().getIdentifierFirstRep().getValue() )
            .setMethod( Bundle.HTTPVerb.POST )
            .setIfNoneExist( "identifier=" + immunizationAndPatient.getPatient().getIdentifierFirstRep().getValue() );

        Bundle.BundleEntryComponent immunizationEntry = new Bundle.BundleEntryComponent();
        immunizationEntry.setResource( immunizationAndPatient.getImmunization() )
            .getRequest().setUrl( "Immunization?identifier=" + immunizationAndPatient.getImmunization().getIdentifierFirstRep().getValue() )
            .setMethod( Bundle.HTTPVerb.POST )
            .setIfNoneExist( "identifier=" + immunizationAndPatient.getImmunization().getIdentifierFirstRep().getValue() );

        return List.of( patientEntry, immunizationEntry );
    }

    @Override
    protected Bundle createBundle()
    {
        return new Bundle().setType( Bundle.BundleType.TRANSACTION );
    }
}
