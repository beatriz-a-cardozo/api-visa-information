package com.tcc.api_visa.notification;

import com.tcc.api_visa.domain.CollectionLog;
import com.tcc.api_visa.service.CollectionRunSummary;
import java.util.Objects;

/** Plain-text description of a run's failures, shared by every notifier. */
final class FailureReport {

    private FailureReport() {}

    static String subject(CollectionRunSummary summary) {
        return "[api-visa] Falha na coleta: %d de %d destino(s)"
                .formatted(summary.failures().size(), summary.logs().size());
    }

    static String body(CollectionRunSummary summary) {
        StringBuilder body = new StringBuilder()
                .append("Coleta ")
                .append(summary.runId())
                .append(", iniciada em ")
                .append(summary.startedAt())
                .append(" (UTC).\n\n");
        for (CollectionLog failure : summary.failures()) {
            body.append("- ")
                    .append(failure.getDestinationIso3())
                    .append(" (")
                    .append(failure.getScraper())
                    .append("): ")
                    .append(failure.getStatus())
                    .append(", ")
                    .append(failure.getPairsCollected())
                    .append(" par(es) coletado(s)\n  ")
                    .append(Objects.requireNonNullElse(failure.getErrorMessage(), "sem mensagem de erro"))
                    .append('\n');
        }
        body.append("\nOs dados anteriores continuam disponíveis na API até a próxima coleta bem-sucedida.\n")
                .append("Detalhes: GET /admin/collections/")
                .append(summary.runId())
                .append('\n');
        return body.toString();
    }
}
