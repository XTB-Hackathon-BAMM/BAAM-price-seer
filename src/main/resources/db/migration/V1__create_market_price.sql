CREATE TABLE market_price (
    id          BIGSERIAL        PRIMARY KEY,
    symbol      VARCHAR(20)      NOT NULL,
    ts          TIMESTAMPTZ      NOT NULL,
    interval    VARCHAR(10)      NOT NULL,
    open        DOUBLE PRECISION NOT NULL,
    high        DOUBLE PRECISION NOT NULL,
    low         DOUBLE PRECISION NOT NULL,
    close       DOUBLE PRECISION NOT NULL,
    received_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (symbol, ts)
);

CREATE INDEX market_price_symbol_ts_idx ON market_price (symbol, ts DESC);
