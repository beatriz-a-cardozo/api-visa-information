package com.tcc.api_visa.api;

import com.tcc.api_visa.service.CollectionAlreadyRunningException;
import com.tcc.api_visa.service.UnknownDestinationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is answered as RFC 9457 Problem Details ({@code application/problem+json}). Internal
 * details (stack traces, exception classes) never reach the client.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CollectionAlreadyRunningException.class)
    ProblemDetail collectionAlreadyRunning(CollectionAlreadyRunningException e) {
        return problem(HttpStatus.CONFLICT, "Collection already running", e.getMessage());
    }

    @ExceptionHandler(UnknownDestinationException.class)
    ProblemDetail unknownDestination(UnknownDestinationException e) {
        return problem(HttpStatus.NOT_FOUND, "Unknown destination", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
