package com.tcc.api_visa.repository;

import com.tcc.api_visa.domain.VisaRequirement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VisaRequirementRepository extends MongoRepository<VisaRequirement, String> {

    Optional<VisaRequirement> findByOriginIso3AndDestinationIso3(String originIso3, String destinationIso3);

    List<VisaRequirement> findByDestinationIso3(String destinationIso3);
}
