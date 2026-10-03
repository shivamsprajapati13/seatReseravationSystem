package seatReservationSystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seatReservationSystem.entity.*;
import seatReservationSystem.exception.*;
import seatReservationSystem.metrics.ReservationMetrics;
import seatReservationSystem.repo.ReservationRepository;
import seatReservationSystem.repo.ReservationSeatRepository;
import seatReservationSystem.repo.SeatRepository;
import seatReservationSystem.repo.ShowRepository;
import seatReservationSystem.repo.ShowUserLockRepository;
import seatReservationSystem.security.ForbiddenException;
import seatReservationSystem.util.RequestHasher;

import java.sql.SQLIntegrityConstraintViolationException;
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
    private final ShowUserLockRepository showUserLockRepository;
    private final ShowUserStateService showUserStateService;
    private final ReservationMetrics metrics;

    @Transactional
    public ReservationResult reserve(
            UUID showId,
            String userId,
            List<String> requestedSeats,
            String idempotencyKey
    ) {
        List<String> seats = normalizeSeats(requestedSeats);
        String requestHash = RequestHasher.hash(seats);

        Optional<ReservationResult> replay =
                replayIfExists(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new NotFoundException("Show not found"));

        ShowUserLock userState =
                showUserStateService.lockForUpdate(showId, userId);

        if (userState.getReservedCount() + seats.size()
                > show.getPerUserLimit()) {
            metrics.userLimitExceeded();
            throw new UserLimitException();
        }

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

        Reservation reservation = new Reservation();
        reservation.setShowId(showId);
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setRequestHash(requestHash);
        reservation.setAmountPaise(show.getPricePaise() * seats.size());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(Instant.now());

        try {
            reservation = reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyDuplicate(ex)) {
                return replayIfExists(userId, idempotencyKey, requestHash)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }

        for (Seat seat : lockedSeats) {
            int updated = seatRepository.confirmSeatIfAvailable(
                    seat.getId(),
                    userId,
                    reservation.getId()
            );
            if (updated == 0) {
                metrics.seatTaken();
                throw new SeatTakenException(seat.getSeatNumber());
            }

            ReservationSeat link = new ReservationSeat();
            link.setReservationId(reservation.getId());
            link.setSeatId(seat.getId());
            link.setSeatNumber(seat.getSeatNumber());
            reservationSeatRepository.save(link);
        }

        userState.setReservedCount(
                userState.getReservedCount() + seats.size()
        );
        showUserLockRepository.save(userState);

        metrics.confirmed(seats.size());

        return new ReservationResult(reservation, seats, false);
    }

    @Transactional
    public ReservationResult cancel(UUID reservationId, String userId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(
                        reservationId
                )
                .orElseThrow(() ->
                        new NotFoundException("Reservation not found")
                );

        if (!reservation.getUserId().equals(userId)) {
            throw new ForbiddenException(
                    "Only the reservation owner may cancel"
            );
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ReservationAlreadyCancelledException();
        }

        List<Seat> seats =
                seatRepository.findByReservationIdForUpdate(reservationId);

        List<String> seatNumbers = seats.stream()
                .map(Seat::getSeatNumber)
                .sorted()
                .toList();

        for (Seat seat : seats) {
            int released = seatRepository.releaseSeatIfOwned(
                    seat.getId(),
                    reservationId
            );
            if (released == 0) {
                throw new SeatTakenException(
                        "Seat no longer owned by reservation: "
                                + seat.getSeatNumber()
                );
            }
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        ShowUserLock userState = showUserStateService.lockForUpdate(
                reservation.getShowId(),
                userId
        );
        userState.setReservedCount(Math.max(
                0,
                userState.getReservedCount() - seatNumbers.size()
        ));
        showUserLockRepository.save(userState);

        metrics.cancelled(seatNumbers.size());

        return new ReservationResult(reservation, seatNumbers, true);
    }

    private Optional<ReservationResult> replayIfExists(
            String userId,
            String idempotencyKey,
            String requestHash
    ) {
        Optional<Reservation> existing =
                reservationRepository.findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Reservation old = existing.get();

        if (!old.getRequestHash().equals(requestHash)) {
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

        return Optional.of(
                new ReservationResult(old, reservedSeats, true)
        );
    }

    private static List<String> normalizeSeats(List<String> requestedSeats) {
        List<String> seats = requestedSeats.stream()
                .distinct()
                .sorted()
                .toList();

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("At least one seat required");
        }
        return seats;
    }

    private static boolean isIdempotencyDuplicate(
            DataIntegrityViolationException ex
    ) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLIntegrityConstraintViolationException sql) {
            String message = sql.getMessage();
            return message != null
                    && message.contains("unique_user_idempotency");
        }
        return false;
    }
}
