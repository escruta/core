package com.escruta.core.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

@Getter
public class DuplicateFieldException extends RuntimeException {
    private final String field;
    private final String value;

    public DuplicateFieldException(String field, String value) {
        super(String.format("The %s '%s' is already in use", field, value));
        this.field = field;
        this.value = value;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(HttpStatus.CONFLICT.value()),
                "Duplicate field value"
        );
        problemDetail.setProperty("field", field);
        problemDetail.setProperty("value", value);
        problemDetail.setProperty("message", getMessage());
        return problemDetail;
    }
}
