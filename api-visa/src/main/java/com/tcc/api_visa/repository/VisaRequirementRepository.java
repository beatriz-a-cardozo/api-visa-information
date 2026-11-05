package com.tcc.api_visa.repository;

import com.tcc.api_visa.domain.TravelPurpose;
import com.tcc.api_visa.domain.VisaRequirement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VisaRequirementRepository extends MongoRepository<VisaRequirement, String> {

    Optional<VisaRequirement> findByOriginIso3AndDestinationIso3AndPurpose(
            String originIso3, String destinationIso3, TravelPurpose purpose);

    List<VisaRequirement> findByDestinationIso3(String destinationIso3);
}
