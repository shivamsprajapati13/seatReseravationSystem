package seatReservationSystem.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import seatReservationSystem.entity.Reservation;
import seatReservationSystem.entity.ReservationStatus;

import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("show_id") UUID showId,
        @JsonProperty("user_id") String userId,
        List<String> seats,
        @JsonProperty("amount_paise") long amountPaise,
        String status
) {

    public static ReservationResponse from(
            Reservation reservation,
            List<String> seats
    ) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                toApiStatus(reservation.getStatus())
        );
    }

    private static String toApiStatus(ReservationStatus status) {
        return switch (status) {
            case CONFIRMED -> "confirmed";
            case CANCELLED -> "cancelled";
        };
    }
}
