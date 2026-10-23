CREATE TABLE room (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    capacity int NOT NULL CHECK (capacity >= 1)
);
