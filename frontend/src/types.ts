export type Role = "GUEST" | "FRONTDESK";
export interface User {
  id: number;
  username: string;
  role: Role;
}
export interface HotelMeta {
  hotelName: string;
  today: string;
  availableFrom: string;
  availableTo: string;
}
export type Status =
  "CONFIRMED" | "CANCELLED" | "NO_SHOW" | "CHECKED_IN" | "COMPLETED";
export interface RoomType {
  id: number;
  name: string;
  description: string;
  capacity: number;
  nightlyPriceCents: number;
  availableRooms: number;
}
export interface BookingPayload {
  roomTypeId: number;
  checkIn: string;
  checkOut: string;
  guestCount: number;
}
export interface Order {
  id: number;
  orderNo: string;
  roomTypeId: number;
  roomTypeName: string;
  checkIn: string;
  checkOut: string;
  guestCount: number;
  nightlyPriceCents: number;
  totalCents: number;
  status: Status;
  createdAt: string;
  stay: null | {
    roomId: number;
    roomNumber: string;
    guestName: string;
    guestPhone: string;
    checkedInAt: string;
    checkedOutAt: string | null;
  };
  events: {
    fromStatus: Status | null;
    toStatus: Status;
    reason: string;
    createdAt: string;
  }[];
}
export interface Room {
  id: number;
  roomNumber: string;
  roomTypeId: number;
  roomTypeName: string;
  cleaningStatus: "CLEAN" | "DIRTY";
  occupied: boolean;
  overdue: boolean;
  currentOrderNo: string | null;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface OrderFilter {
  page: number;
  size: number;
  status?: string;
  orderNo?: string;
  checkIn?: string;
}
