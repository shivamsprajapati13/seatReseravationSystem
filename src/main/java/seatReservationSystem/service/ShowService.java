package seatReservationSystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seatReservationSystem.dto.response.SeatCounts;
import seatReservationSystem.dto.response.SeatView;
import seatReservationSystem.dto.response.ShowDetailResponse;
import seatReservationSystem.dto.response.ShowResponse;
import seatReservationSystem.entity.Seat;
import seatReservationSystem.entity.SeatStatus;
import seatReservationSystem.entity.Show;
import seatReservationSystem.exception.NotFoundException;
import seatReservationSystem.repo.SeatRepository;
import seatReservationSystem.repo.ShowRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShowService {

    private static final int DEFAULT_PER_USER_LIMIT = 4;

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public ShowResponse createShow(
            String name,
            List<String> seatNumbers,
            long pricePaise,
            Integer perUserLimit
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Show name is required");
        }
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new IllegalArgumentException("At least one seat is required");
        }
        if (pricePaise < 0) {
            throw new IllegalArgumentException("price_paise must be non-negative");
        }

        List<String> seats = seatNumbers.stream()
                .distinct()
                .sorted()
                .toList();

        if (seats.size() != seatNumbers.size()) {
            throw new IllegalArgumentException("Duplicate seat numbers are not allowed");
        }

        Show show = new Show();
        show.setName(name.trim());
        show.setPricePaise(pricePaise);
        show.setPerUserLimit(
                perUserLimit != null ? perUserLimit : DEFAULT_PER_USER_LIMIT
        );
        show.setCreatedAt(Instant.now());
        show = showRepository.save(show);

        List<SeatView> seatViews = new ArrayList<>();
        for (String seatNumber : seats) {
            Seat seat = new Seat();
            seat.setShow(show);
            seat.setSeatNumber(seatNumber);
            seat.setStatus(SeatStatus.AVAILABLE);
            seatRepository.save(seat);
            seatViews.add(SeatView.from(seat));
        }

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                seatViews
        );
    }

    @Transactional(readOnly = true)
    public ShowDetailResponse getShow(UUID showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new NotFoundException("Show not found"));

        List<Seat> seats = seatRepository.findByShow_IdOrderBySeatNumberAsc(showId);

        int available = 0;
        int held = 0;
        int confirmed = 0;
        List<SeatView> seatViews = new ArrayList<>();

        for (Seat seat : seats) {
            seatViews.add(SeatView.from(seat));
            switch (seat.getStatus()) {
                case AVAILABLE -> available++;
                case HELD -> held++;
                case CONFIRMED -> confirmed++;
            }
        }

        SeatCounts counts = new SeatCounts(
                available,
                held,
                confirmed,
                seats.size()
        );

        return new ShowDetailResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                seatViews,
                counts
        );
    }
}
