package com.famelsvg.reservasalas.room;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class Room {

    @Id
    private UUID id;
    private String name;
    private int capacity;

    protected Room() {}

    public Room(String name, int capacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }

    public void update(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }
}
