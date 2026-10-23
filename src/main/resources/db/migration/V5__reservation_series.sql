ALTER TABLE reservation ADD COLUMN series_id uuid;

CREATE INDEX reservation_series_idx ON reservation (series_id) WHERE series_id IS NOT NULL;
