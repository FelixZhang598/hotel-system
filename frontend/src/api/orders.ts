import { request, queryString } from "./http";
import type { Order, Page, OrderFilter, BookingPayload } from "../types";
export const createOrder = (payload: BookingPayload, key: string) =>
  request<Order>("/orders", {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(payload),
  });
export const listOrders = (filter: OrderFilter) =>
  request<Page<Order>>("/orders?" + queryString(filter));
export const getOrder = (id: number) => request<Order>("/orders/" + id);
export const cancelOrder = (id: number) =>
  request<Order>("/orders/" + id + "/cancel", { method: "POST" });
