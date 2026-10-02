package seatReservationSystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import seatReservationSystem.security.ForbiddenException;
import seatReservationSystem.security.UnauthorizedException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatTakenException.class)
    ResponseEntity<?> seatTaken(SeatTakenException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "SEAT_TAKEN",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(UserLimitException.class)
    ResponseEntity<?> userLimit() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "PER_USER_LIMIT"));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<?> idempotencyConflict() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "IDEMPOTENCY_KEY_REUSED"));
    }

    @ExceptionHandler(SeatNotFoundException.class)
    ResponseEntity<?> seatNotFound(SeatNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "SEAT_NOT_FOUND",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<?> notFound(NotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "NOT_FOUND",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "BAD_REQUEST",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<?> unauthorized(UnauthorizedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "UNAUTHORIZED",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<?> forbidden(ForbiddenException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "FORBIDDEN",
                        "message", ex.getMessage()
                ));
    }
}
