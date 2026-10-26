CREATE TABLE kommunicering_common_data (
    handlaggning_id              UUID        NOT NULL PRIMARY KEY,
    uppgift_id                   UUID        NOT NULL,
    uppgift_version              INTEGER     NOT NULL,
    uppgift_skapad_ts            TIMESTAMPTZ NOT NULL,
    uppgift_utford_ts            TIMESTAMPTZ,
    uppgift_planerad_ts          TIMESTAMPTZ,
    uppgift_utforar_id_typ_id    VARCHAR(255),
    uppgift_utforar_id_varde     VARCHAR(255),
    uppgift_status               VARCHAR(255) NOT NULL,
    uppgift_aktivitet_id         UUID        NOT NULL,
    uppgift_fssa_information     VARCHAR(255) NOT NULL,
    uppgift_specifikation_id     UUID        NOT NULL,
    uppgift_specifikation_version INTEGER    NOT NULL,
    oul_uppgift_id               UUID,
    version                      BIGINT      NOT NULL DEFAULT 0,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL
);

CREATE TABLE kommunicering_cloud_event_data (
    handlaggning_id        UUID         NOT NULL PRIMARY KEY,
    event_id               UUID         NOT NULL,
    kogitorootprociid      UUID         NOT NULL,
    kogitoparentprociid    UUID         NOT NULL,
    kogitoprocinstanceid   UUID         NOT NULL,
    kogitorootprocid       VARCHAR(255) NOT NULL,
    kogitoprocid           VARCHAR(255) NOT NULL,
    kogitoprocist          VARCHAR(255),
    kogitoprocversion      VARCHAR(255),
    type                   VARCHAR(255) NOT NULL,
    source                 VARCHAR(255) NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL
);
