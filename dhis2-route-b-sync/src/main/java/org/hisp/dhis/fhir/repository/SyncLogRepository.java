package org.hisp.dhis.fhir.repository;

import org.hisp.dhis.fhir.domain.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long>
{
}
