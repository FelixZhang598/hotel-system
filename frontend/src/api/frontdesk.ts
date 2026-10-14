import { request, queryString } from "./http";
import type { Order, Page, OrderFilter, Room } from "../types";
export const listOrders = (filter: OrderFilter) =>
  request<Page<Order>>("/frontdesk/orders?" + queryString(filter));
export const getOrder = (id: number) =>
  request<Order>("/frontdesk/orders/" + id);
export const availableRooms = (id: number) =>
  request<Room[]>("/frontdesk/orders/" + id + "/available-rooms");
export const action = (
  id: number,
  action: "cancel" | "no-show" | "check-out",
) =>
  request<Order>("/frontdesk/orders/" + id + "/" + action, { method: "POST" });
export const checkIn = (
  id: number,
  payload: { roomId: number; guestName: string; guestPhone: string },
) =>
  request<Order>("/frontdesk/orders/" + id + "/check-in", {
    method: "POST",
    body: JSON.stringify(payload),
  });
export const rooms = () => request<Room[]>("/frontdesk/rooms");
export const clean = (id: number) =>
  request<Room>("/frontdesk/rooms/" + id + "/clean", { method: "POST" });
