package com.tcc.api_visa.api;

import com.tcc.api_visa.service.CollectionAlreadyRunningException;
import com.tcc.api_visa.service.SameCountryException;
import com.tcc.api_visa.service.UnknownCountryException;
import com.tcc.api_visa.service.UnknownDestinationException;
import com.tcc.api_visa.service.VisaDataNotFoundException;
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

    @ExceptionHandler(UnknownCountryException.class)
    ProblemDetail unknownCountry(UnknownCountryException e) {
        return problem(HttpStatus.NOT_FOUND, "Unknown country", e.getMessage());
    }

    @ExceptionHandler(VisaDataNotFoundException.class)
    ProblemDetail visaDataNotFound(VisaDataNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "No visa data", e.getMessage());
    }

    @ExceptionHandler(SameCountryException.class)
    ProblemDetail sameCountry(SameCountryException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid country pair", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
