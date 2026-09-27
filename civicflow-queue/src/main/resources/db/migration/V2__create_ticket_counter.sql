CREATE TABLE queue_ticket_counter (
    outlet_id BIGINT UNSIGNED NOT NULL,
    service_date DATE NOT NULL,
    next_number INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (outlet_id, service_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
