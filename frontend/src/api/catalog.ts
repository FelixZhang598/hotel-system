import { request, queryString } from "./http";
import type { RoomType } from "../types";
export const searchRooms = (checkIn: string, checkOut: string) =>
  request<RoomType[]>("/room-types?" + queryString({ checkIn, checkOut }));
