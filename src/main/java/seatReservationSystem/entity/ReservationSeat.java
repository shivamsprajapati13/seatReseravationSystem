package seatReservationSystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(
        name = "reservation_seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_reserved_seat",
                        columnNames = "seat_id"
                )
        }
)
@Data
@IdClass(ReservationSeatId.class)
public class ReservationSeat {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Id
    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;
}
