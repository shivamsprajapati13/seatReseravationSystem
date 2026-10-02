package seatReservationSystem.entity;



import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Entity
@Data
@Table(
        name = "reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_user_idempotency",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)

public class Reservation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "show_id")
    private UUID showId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "amount_paise")
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(name = "created_at")
    private Instant createdAt;
}