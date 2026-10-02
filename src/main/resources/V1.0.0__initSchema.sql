CREATE TABLE shows (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_paise BIGINT NOT NULL,
    per_user_limit INT NOT NULL DEFAULT 4,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT chk_show_price
        CHECK (price_paise >= 0),

    CONSTRAINT chk_user_limit
        CHECK (per_user_limit > 0)
) ENGINE=InnoDB;


CREATE TABLE seats (
    id CHAR(36) PRIMARY KEY,
    show_id CHAR(36) NOT NULL,
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    held_by VARCHAR(255),
    reservation_id CHAR(36),
    hold_expires_at TIMESTAMP(6),

    CONSTRAINT fk_seat_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_seat_status
        CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),

    CONSTRAINT unique_show_seat
        UNIQUE (show_id, seat_number),

    INDEX idx_seats_show_status (show_id, status),
    INDEX idx_seats_show_user (show_id, held_by)
) ENGINE=InnoDB;


CREATE TABLE reservations (
    id CHAR(36) PRIMARY KEY,
    show_id CHAR(36) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_reservation_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id),

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED')),

    CONSTRAINT unique_user_idempotency
        UNIQUE (user_id, idempotency_key),

    INDEX idx_reservation_show_user (show_id, user_id)
) ENGINE=InnoDB;


CREATE TABLE reservation_seats (
    reservation_id CHAR(36) NOT NULL,
    seat_id CHAR(36) NOT NULL,
    seat_number VARCHAR(50) NOT NULL,

    PRIMARY KEY (reservation_id, seat_id),

    CONSTRAINT unique_reserved_seat
        UNIQUE (seat_id),

    CONSTRAINT fk_reservation_seats_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_reservation_seats_seat
        FOREIGN KEY (seat_id)
        REFERENCES seats(id)
) ENGINE=InnoDB;


CREATE TABLE show_user_state (
    show_id CHAR(36) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,

    PRIMARY KEY (show_id, user_id),

    CONSTRAINT fk_show_user_state_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;