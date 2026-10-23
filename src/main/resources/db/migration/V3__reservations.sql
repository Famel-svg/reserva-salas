CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservation (
    id uuid PRIMARY KEY,
    room_id uuid NOT NULL REFERENCES room (id),
    user_id uuid NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    CHECK (starts_at < ends_at),
    -- Garantia real de não sobreposição; intervalo semiaberto permite 10h-11h e 11h-12h.
    -- room_id::text porque btree_gist só suporta uuid a partir do PostgreSQL 18.
    CONSTRAINT reservation_no_overlap EXCLUDE USING gist (
        (room_id::text) WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'ACTIVE')
);

CREATE INDEX reservation_user_idx ON reservation (user_id);
