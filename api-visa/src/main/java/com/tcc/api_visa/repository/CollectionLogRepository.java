package com.tcc.api_visa.repository;

import com.tcc.api_visa.domain.CollectionLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionLogRepository extends MongoRepository<CollectionLog, String> {

    Optional<CollectionLog> findFirstByDestinationIso3OrderByStartedAtDesc(String destinationIso3);

    List<CollectionLog> findByOrderByStartedAtDesc(Pageable pageable);

    List<CollectionLog> findByDestinationIso3OrderByStartedAtDesc(String destinationIso3, Pageable pageable);

    List<CollectionLog> findByRunIdOrderByStartedAtAsc(String runId);
}
