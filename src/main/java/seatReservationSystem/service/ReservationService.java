package seatReservationSystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seatReservationSystem.entity.*;
import seatReservationSystem.exception.*;
import seatReservationSystem.metrics.ReservationMetrics;
import seatReservationSystem.repo.ReservationRepository;
import seatReservationSystem.repo.ReservationSeatRepository;
import seatReservationSystem.repo.SeatRepository;
import seatReservationSystem.repo.ShowRepository;
import seatReservationSystem.util.RequestHasher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationMetrics metrics;

    @Transactional
    public ReservationResult reserve(
            UUID showId,
            String userId,
            List<String> requestedSeats,
            String idempotencyKey
    ) {
        List<String> seats = requestedSeats.stream()
                .distinct()
                .sorted()
                .toList();

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("At least one seat required");
        }

        Optional<Reservation> existing =
                reservationRepository.findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );

        if (existing.isPresent()) {
            Reservation old = existing.get();
            String newHash = RequestHasher.hash(seats);

            if (!old.getRequestHash().equals(newHash)) {
                metrics.idempotencyConflict();
                throw new IdempotencyConflictException();
            }

            metrics.idempotentReplay();

            List<String> reservedSeats =
                    reservationSeatRepository
                            .findByReservationIdOrderBySeatNumberAsc(old.getId())
                            .stream()
                            .map(ReservationSeat::getSeatNumber)
                            .toList();

            return new ReservationResult(old, reservedSeats, true);
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new NotFoundException("Show not found"));

        List<Seat> lockedSeats =
                seatRepository.findSeatsForUpdate(showId, seats);

        if (lockedSeats.size() != seats.size()) {
            throw new SeatNotFoundException();
        }

        for (Seat seat : lockedSeats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                metrics.seatTaken();
                throw new SeatTakenException(seat.getSeatNumber());
            }
        }

        long existingCount =
                reservationRepository.countByShowIdAndUserIdAndStatus(
                        showId,
                        userId,
                        ReservationStatus.CONFIRMED
                );

        if (existingCount + seats.size() > show.getPerUserLimit()) {
            metrics.userLimitExceeded();
            throw new UserLimitException();
        }

        Reservation reservation = new Reservation();
        reservation.setShowId(showId);
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setRequestHash(RequestHasher.hash(seats));
        reservation.setAmountPaise(show.getPricePaise() * seats.size());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(Instant.now());
        reservation = reservationRepository.save(reservation);

        for (Seat seat : lockedSeats) {
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setHeldBy(userId);
            seat.setReservationId(reservation.getId());

            ReservationSeat link = new ReservationSeat();
            link.setReservationId(reservation.getId());
            link.setSeatId(seat.getId());
            link.setSeatNumber(seat.getSeatNumber());
            reservationSeatRepository.save(link);
        }

        metrics.confirmed(seats.size());

        return new ReservationResult(reservation, seats, false);
    }
}
