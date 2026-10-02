package seatReservationSystem.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import seatReservationSystem.dto.request.ReserveRequest;
import seatReservationSystem.dto.response.ReservationResponse;
import seatReservationSystem.security.UserIdentity;
import seatReservationSystem.service.ReservationResult;
import seatReservationSystem.service.ReservationService;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final UserIdentity userIdentity;

    @PostMapping("/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyHeader,
            @RequestBody ReserveRequest request,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = resolveIdempotencyKey(idempotencyHeader, request);
        String userId = userIdentity.getUserId(httpRequest);

        ReservationResult result = reservationService.reserve(
                showId,
                userId,
                request.seats(),
                idempotencyKey
        );

        HttpStatus status = result.replayed()
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity
                .status(status)
                .body(ReservationResponse.from(
                        result.reservation(),
                        result.seats()
                ));
    }

    private static String resolveIdempotencyKey(
            String header,
            ReserveRequest request
    ) {
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        if (request != null && StringUtils.hasText(request.idempotencyKey())) {
            return request.idempotencyKey().trim();
        }
        throw new IllegalArgumentException("Idempotency key is required");
    }
}
