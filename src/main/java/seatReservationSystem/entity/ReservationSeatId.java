package seatReservationSystem.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeatId implements Serializable {

    private UUID reservationId;

    private UUID seatId;
}
