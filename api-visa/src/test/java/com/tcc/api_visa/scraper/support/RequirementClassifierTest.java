package com.tcc.api_visa.scraper.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.domain.RequirementType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RequirementClassifierTest {

    private final RequirementClassifier classifier = new RequirementClassifier();

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "have a valid travel document, like a passport | DOCUMENT",
                "Passaporte válido por pelo menos seis meses      | DOCUMENT",
                "Gültiger Reisepass                               | DOCUMENT",
                "have enough money for your stay                  | FINANCIAL_PROOF",
                "Comprovante de meios de subsistência             | FINANCIAL_PROOF",
                "pay for your return or onward journey (or have funding from someone else) | RETURN_TICKET",
                "be in good health                                | HEALTH",
                "have no criminal or immigration-related convictions | CRIMINAL_RECORD",
                "a letter of invitation from someone in Canada    | INVITATION_LETTER",
                "Carta de invitación                              | INVITATION_LETTER",
                "Seguro viagem com cobertura mínima               | TRAVEL_INSURANCE",
                "two recent photos                                | PHOTO",
                "pay the application fee                          | FEE_PAYMENT",
                "proof of accommodation                           | ACCOMMODATION",
                "convince an officer you will leave at the end of your visit | OTHER"
            })
    void classifiesByKeywordInSeveralLanguages(String text, RequirementType expected) {
        assertThat(classifier.classify(text)).isEqualTo(expected);
    }
}
