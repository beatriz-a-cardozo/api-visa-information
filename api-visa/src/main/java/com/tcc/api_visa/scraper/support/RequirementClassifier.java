package com.tcc.api_visa.scraper.support;

import com.tcc.api_visa.domain.RequirementType;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Assigns a {@link RequirementType} to a requirement sentence by keyword. Keywords cover the
 * languages of the sources in scope (en, pt, es, de) and are compared without accents. The first
 * matching type wins, so more specific types come first.
 */
@Component
public class RequirementClassifier {

    private static final Map<RequirementType, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(
                RequirementType.TRAVEL_INSURANCE,
                List.of(
                        "travel insurance",
                        "health insurance",
                        "seguro viagem",
                        "seguro de viaje",
                        "krankenversicherung"));
        KEYWORDS.put(
                RequirementType.INVITATION_LETTER,
                List.of("invitation", "carta convite", "convite", "invitacion", "einladung"));
        KEYWORDS.put(
                RequirementType.CRIMINAL_RECORD,
                List.of(
                        "criminal",
                        "conviction",
                        "police certificate",
                        "antecedentes",
                        "certidao de antecedentes",
                        "fuhrungszeugnis"));
        KEYWORDS.put(
                RequirementType.RETURN_TICKET,
                List.of(
                        "return or onward",
                        "onward journey",
                        "return ticket",
                        "passagem de volta",
                        "boleto de regreso",
                        "ruckflug"));
        KEYWORDS.put(
                RequirementType.FINANCIAL_PROOF,
                List.of(
                        "enough money",
                        "support yourself",
                        "funding",
                        "funds",
                        "financial",
                        "bank statement",
                        "meios de subsistencia",
                        "comprovante financeiro",
                        "solvencia",
                        "finanzierung"));
        KEYWORDS.put(
                RequirementType.ACCOMMODATION, List.of("accommodation", "hospedagem", "alojamiento", "unterkunft"));
        KEYWORDS.put(
                RequirementType.HEALTH,
                List.of("health", "medical", "vaccin", "saude", "medico", "salud", "gesundheit"));
        KEYWORDS.put(RequirementType.PHOTO, List.of("photo", "foto", "lichtbild"));
        KEYWORDS.put(RequirementType.FEE_PAYMENT, List.of("fee", "payment", "taxa", "pagamento", "tasa", "gebuhr"));
        KEYWORDS.put(
                RequirementType.DOCUMENT,
                List.of("passport", "travel document", "passaporte", "pasaporte", "reisepass", "identity document"));
    }

    public RequirementType classify(String text) {
        String normalized = normalize(text);
        return KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(RequirementType.OTHER);
    }

    static String normalize(String text) {
        String withoutAccents =
                Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }
}
