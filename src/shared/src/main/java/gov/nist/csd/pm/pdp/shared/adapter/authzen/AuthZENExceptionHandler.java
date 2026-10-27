/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.shared.adapter.authzen;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Returns AuthZEN error responses: an HTTP error status with an error message string body.
 */
@RestControllerAdvice(assignableTypes = AuthZENController.class)
public class AuthZENExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthZENExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadable(HttpMessageNotReadableException e) {
        if (isInvalidSemantic(e)) {
            return error(HttpStatus.BAD_REQUEST,
                    "unknown options.evaluations_semantic; expected execute_all, deny_on_first_deny or permit_on_first_permit");
        }

        return error(HttpStatus.BAD_REQUEST, "malformed request body");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception e) {
        HttpStatus status = AuthZENErrors.statusOf(e);
        if (status.is5xxServerError()) {
            logger.error("authzen evaluation FAILED", e);
        }

        return error(status, e.getMessage());
    }

    private static boolean isInvalidSemantic(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof InvalidFormatException ife && ife.getTargetType() == EvaluationsSemantic.class) {
                return true;
            }
        }

        return false;
    }

    private static ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message != null ? message : status.getReasonPhrase());
    }
}
