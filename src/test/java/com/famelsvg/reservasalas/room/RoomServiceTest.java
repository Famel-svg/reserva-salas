package com.famelsvg.reservasalas.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.famelsvg.reservasalas.common.error.InvalidRequestException;
import com.famelsvg.reservasalas.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository rooms;
    @InjectMocks RoomService service;

    @Test
    void listsAll() {
        Room r = new Room("A", 4);
        when(rooms.findAll()).thenReturn(List.of(r));
        assertThat(service.list()).containsExactly(r);
    }

    @Test
    void getsExisting() {
        Room r = new Room("A", 4);
        when(rooms.findById(r.getId())).thenReturn(Optional.of(r));
        assertThat(service.get(r.getId())).isSameAs(r);
    }

    @Test
    void getUnknownThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(rooms.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createsRoom() {
        when(rooms.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));
        Room r = service.create("Sala", 8);
        assertThat(r.getName()).isEqualTo("Sala");
        assertThat(r.getCapacity()).isEqualTo(8);
        assertThat(r.getId()).isNotNull();
    }

    @Test
    void createRejectsCapacityBelowOne() {
        assertThatThrownBy(() -> service.create("Sala", 0)).isInstanceOf(InvalidRequestException.class);
        verify(rooms, never()).save(any());
    }

    @Test
    void updatesRoom() {
        Room r = new Room("A", 4);
        when(rooms.findById(r.getId())).thenReturn(Optional.of(r));
        Room updated = service.update(r.getId(), "B", 6);
        assertThat(updated.getName()).isEqualTo("B");
        assertThat(updated.getCapacity()).isEqualTo(6);
    }

    @Test
    void updateRejectsInvalidCapacity() {
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "B", -1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateUnknownThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(rooms.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(id, "B", 2)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesRoom() {
        Room r = new Room("A", 4);
        when(rooms.findById(r.getId())).thenReturn(Optional.of(r));
        service.delete(r.getId());
        verify(rooms).delete(r);
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(rooms.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(rooms, never()).delete(any());
    }
}
