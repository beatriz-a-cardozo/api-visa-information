package com.tcc.api_visa.repository;

import com.tcc.api_visa.domain.VisaHistory;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VisaHistoryRepository extends MongoRepository<VisaHistory, String> {

    List<VisaHistory> findByOriginIso3AndDestinationIso3OrderByChangedAtDesc(String originIso3, String destinationIso3);
}
