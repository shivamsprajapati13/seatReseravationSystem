package seatReservationSystem.exception;

/**
 * Contention that clients may retry (mapped to 409, not 5xx).
 */
public class TransientConflictException extends RuntimeException {

    public TransientConflictException(String message) {
        super(message);
    }
}
