package seatReservationSystem.exception;

public class SeatTakenException extends RuntimeException {

    public SeatTakenException(String seatNumber) {
        super("Seat already taken: " + seatNumber);
    }
}
