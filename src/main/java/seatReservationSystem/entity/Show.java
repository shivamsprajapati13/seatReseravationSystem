package seatReservationSystem.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shows")
@Data
public class Show {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @Column(name = "price_paise")
    private long pricePaise;

    @Column(name = "per_user_limit")
    private int perUserLimit = 4;

    @Column(name = "created_at")
    private Instant createdAt;
}