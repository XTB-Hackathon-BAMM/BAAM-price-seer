CREATE TABLE sent_prediction (
    id        BIGSERIAL    PRIMARY KEY,
    symbol    VARCHAR(20)  NOT NULL,
    minute    TIMESTAMPTZ  NOT NULL,
    direction VARCHAR(10)  NOT NULL,
    sent_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (symbol, minute)
);
