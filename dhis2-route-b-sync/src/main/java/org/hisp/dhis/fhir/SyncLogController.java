package org.hisp.dhis.fhir;

import lombok.RequiredArgsConstructor;
import org.hisp.dhis.fhir.domain.SyncLog;
import org.hisp.dhis.fhir.repository.SyncLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping( "/api/sync-log" )
@RequiredArgsConstructor
public class SyncLogController
{
    private final SyncLogRepository syncLogRepository;

    @GetMapping
    public List<SyncLog> history()
    {
        return syncLogRepository.findAll( Sort.by( Sort.Direction.DESC, "timestamp" ) );
    }
}
