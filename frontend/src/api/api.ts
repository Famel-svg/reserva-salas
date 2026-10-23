import { json, request, setTokens } from './client';

export type Role = 'MEMBER' | 'ADMIN';
export interface Room {
  id: string;
  name: string;
  capacity: number;
}
export interface Reservation {
  id: string;
  roomId: string;
  userId: string;
  startsAt: string;
  endsAt: string;
  status: 'ACTIVE' | 'CANCELLED';
  seriesId: string | null;
}

export async function login(email: string, password: string) {
  const t = await request<{ accessToken: string; refreshToken: string }>(
    '/auth/login',
    json('POST', { email, password }),
    false,
  );
  setTokens({ accessToken: t.accessToken, refreshToken: t.refreshToken });
}

export const listRooms = () => request<Room[]>('/rooms');
export const saveRoom = (r: { name: string; capacity: number }, id?: string) =>
  request<Room>(id ? `/rooms/${id}` : '/rooms', json(id ? 'PUT' : 'POST', r));
export const deleteRoom = (id: string) => request<void>(`/rooms/${id}`, json('DELETE'));

export const myReservations = () => request<Reservation[]>('/reservations/mine');
export const allReservations = () => request<Reservation[]>('/reservations');
export const reserve = (b: { roomId: string; startsAt: string; endsAt: string }) =>
  request<Reservation>('/reservations', json('POST', b));
export const reserveRecurring = (b: { roomId: string; startsAt: string; endsAt: string; occurrences: number }) =>
  request<Reservation[]>('/reservations/recurring', json('POST', b));
export const cancelReservation = (id: string) => request<Reservation>(`/reservations/${id}`, json('DELETE'));
export const cancelSeries = (id: string) =>
  request<{ seriesId: string; cancelled: number }>(`/reservations/series/${id}`, json('DELETE'));

export const createUser = (b: { email: string; password: string; role: Role }) =>
  request<{ id: string; email: string; role: Role }>('/users', json('POST', b));
