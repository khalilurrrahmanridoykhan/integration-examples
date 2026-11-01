package org.hisp.dhis.fhir.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// One row of the vaccine catalog -- which DHIS2 data element/program stage holds this
// vaccine's dose, and which FHIR CVX code represents it on the Immunization side.
@Getter
@RequiredArgsConstructor
public class VaccineConfig
{
    private final String dataElementId;
    private final String programStageId;
    private final String cvxCode;
    private final String cvxDisplay;

    // true: the DHIS2 data element is BOOLEAN ("true"/"false" -- a dose was given or not).
    // false: the data element is TEXT holding a dose number (e.g. "1", "2") -- any non-empty
    // value means a dose was given. Route A always writes "true" or "1" respectively, since
    // FHIR Immunization doesn't reliably carry a DHIS2-style dose-number string back.
    private final boolean booleanStyle;

    public boolean isGiven( String dataValue )
    {
        if ( booleanStyle )
        {
            return "true".equals( dataValue );
        }
        return dataValue != null && !dataValue.isBlank();
    }

    public String valueWhenGiven()
    {
        return booleanStyle ? "true" : "1";
    }
}
