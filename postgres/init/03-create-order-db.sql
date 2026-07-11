-- DB-per-service: order-service owns its own relational database (orders).
-- Runs only on first initialization of an empty data volume.
CREATE DATABASE kafka_order OWNER kafka;
