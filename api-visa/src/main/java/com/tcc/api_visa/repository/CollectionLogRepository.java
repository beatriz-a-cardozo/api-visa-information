package com.tcc.api_visa.repository;

import com.tcc.api_visa.domain.CollectionLog;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionLogRepository extends MongoRepository<CollectionLog, String> {

    Optional<CollectionLog> findFirstByDestinationIso3OrderByStartedAtDesc(String destinationIso3);
}
