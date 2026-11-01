/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.fhir.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Component
@Validated
@ConfigurationProperties( "dhis2-to-fhir.dhis2" )
public class DhisProperties
{
    @NotNull
    private String baseUrl;

    @NotNull
    private String username;

    @NotNull
    private String password;

    // Which DHIS2 program/org unit Route A and B operate against, and which of that
    // program's data elements represent vaccine doses. The field defaults below reproduce
    // the DHIS2 demo's "Child Programme" exactly as this service originally shipped, but
    // every value is overridable in application.yml -- pointing this service at a different
    // program/org unit/vaccine set is then a config change, not a recompile.
    @NotNull
    private String orgUnit = "DiszpKrYNg8";

    @NotNull
    private String program = "IpHINAT79UW";

    private List<VaccineProperties> vaccines = List.of(
        new VaccineProperties( "bx6fsa0t90x", "A03MvHHogjR", "19", "BCG", true ),
        new VaccineProperties( "ebaJjqltK5N", "A03MvHHogjR", "02", "OPV", false ),
        new VaccineProperties( "FqlgKAG8HOu", "ZzYYXq4fJie", "05", "measles", true ),
        new VaccineProperties( "rxBfISxXS2U", "ZzYYXq4fJie", "37", "yellow fever", true ),
        new VaccineProperties( "vTUhAUZFoys", "ZzYYXq4fJie", "110", "DTaP-Hep B-IPV (Penta)", false ),
        new VaccineProperties( "pOe0ogW4OWd", "ZzYYXq4fJie", "107", "DTaP, unspecified formulation", false )
    );

    // Which DHIS2 tracked entity type/attributes carry a person's demographics -- used both
    // when creating a brand-new tracked entity for an unmapped FHIR patient (Route A) and
    // when reading an existing one back out as a FHIR Patient (Route B's tracked-entity
    // sync). Defaults match the DHIS2 demo's "Person" tracked entity type.
    @NotNull
    private String trackedEntityType = "nEenWmSyUEp";

    @NotNull
    private String genderAttribute = "cejWyOfXge6";

    @NotNull
    private String firstNameAttribute = "w75KJ2mc4zz";

    @NotNull
    private String lastNameAttribute = "zDhUuAYrxNC";

    // One row of the configured vaccine catalog -- see VaccineCatalog, which turns these
    // into VaccineConfig instances at startup.
    @Data
    public static class VaccineProperties
    {
        @NotNull
        private String dataElementId;

        @NotNull
        private String programStage;

        @NotNull
        private String cvxCode;

        @NotNull
        private String cvxDisplay;

        private boolean booleanStyle;

        public VaccineProperties()
        {
        }

        public VaccineProperties( String dataElementId, String programStage, String cvxCode, String cvxDisplay,
            boolean booleanStyle )
        {
            this.dataElementId = dataElementId;
            this.programStage = programStage;
            this.cvxCode = cvxCode;
            this.cvxDisplay = cvxDisplay;
            this.booleanStyle = booleanStyle;
        }
    }
}
