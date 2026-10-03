package seatReservationSystem.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seatReservationSystem.dto.response.ReservationResponse;
import seatReservationSystem.security.UserIdentity;
import seatReservationSystem.service.ReservationResult;
import seatReservationSystem.service.ReservationService;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationCancelController {

    private final ReservationService reservationService;
    private final UserIdentity userIdentity;

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID reservationId,
            HttpServletRequest httpRequest
    ) {
        String userId = userIdentity.getUserId(httpRequest);

        ReservationResult result = reservationService.cancel(
                reservationId,
                userId
        );

        return ResponseEntity.ok(
                ReservationResponse.from(
                        result.reservation(),
                        result.seats()
                )
        );
    }
}
