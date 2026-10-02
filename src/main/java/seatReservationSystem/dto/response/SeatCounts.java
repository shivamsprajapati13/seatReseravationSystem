package seatReservationSystem.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SeatCounts(
        int available,
        int held,
        int confirmed,
        @JsonProperty("total_seats") int totalSeats
) {
}
