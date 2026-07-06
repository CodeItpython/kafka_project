-- DB-per-service: shopping-service owns its own relational database (cart).
-- Runs only on first initialization of an empty data volume.
CREATE DATABASE kafka_shopping OWNER kafka;
