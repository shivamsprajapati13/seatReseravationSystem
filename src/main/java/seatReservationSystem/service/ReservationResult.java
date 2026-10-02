package seatReservationSystem.service;

import seatReservationSystem.entity.Reservation;

import java.util.List;

public record ReservationResult(
        Reservation reservation,
        List<String> seats,
        boolean replayed
) {
}
