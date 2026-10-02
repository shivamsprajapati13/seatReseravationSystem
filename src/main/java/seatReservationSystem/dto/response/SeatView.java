package seatReservationSystem.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import seatReservationSystem.entity.Seat;
import seatReservationSystem.entity.SeatStatus;

public record SeatView(
        @JsonProperty("seat_number") String seatNumber,
        String status
) {

    public static SeatView from(Seat seat) {
        return new SeatView(
                seat.getSeatNumber(),
                toApiStatus(seat.getStatus())
        );
    }

    private static String toApiStatus(SeatStatus status) {
        return switch (status) {
            case AVAILABLE -> "available";
            case HELD -> "held";
            case CONFIRMED -> "confirmed";
        };
    }
}
