package seatReservationSystem.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CreateShowRequest(
        String name,
        List<String> seats,

        @JsonProperty("price_paise")
        long pricePaise,

        @JsonProperty("per_user_limit")
        Integer perUserLimit
) {
}
