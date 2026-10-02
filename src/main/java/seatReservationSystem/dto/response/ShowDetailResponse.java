package seatReservationSystem.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ShowDetailResponse(
        UUID id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        List<SeatView> seats,
        SeatCounts counts
) {
}
