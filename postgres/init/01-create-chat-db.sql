-- DB-per-service: chat-service owns its own relational database on the same
-- Postgres instance. Runs only on first initialization of an empty data volume.
CREATE DATABASE kafka_chat OWNER kafka;
