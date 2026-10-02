package seatReservationSystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_show_seat",
                        columnNames = {"show_id", "seat_number"}
                )
        }
)

public class Seat {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id")
    private Show show;

    @Column(name = "seat_number")
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @Column(name = "held_by")
    private String heldBy;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;
}