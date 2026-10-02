package seatReservationSystem.exception;

public class SeatNotFoundException extends RuntimeException {

    public SeatNotFoundException() {
        super("One or more seats do not exist for this show");
    }
}
