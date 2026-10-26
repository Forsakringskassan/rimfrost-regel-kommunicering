CREATE TABLE kommunicering_process_topic_info(
    handlaggning_id     UUID         NOT NULL PRIMARY KEY,
    reply_topic         VARCHAR(255) NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);