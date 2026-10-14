import type { Status } from "./types";
export const statusLabels: Record<Status, string> = {
  CONFIRMED: "已确认",
  CANCELLED: "已取消",
  NO_SHOW: "未到店",
  CHECKED_IN: "已入住",
  COMPLETED: "已完成",
};
export const statusType: Record<
  Status,
  "success" | "warning" | "info" | "danger" | "primary"
> = {
  CONFIRMED: "primary",
  CANCELLED: "info",
  NO_SHOW: "danger",
  CHECKED_IN: "success",
  COMPLETED: "info",
};
export const money = (cents: number) =>
  new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 0,
  }).format(cents / 100);
export const time = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(value))
    : "—";
export const addDays = (date: string, days: number) => {
  const d = new Date(date + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};
export const nights = (start: string, end: string) =>
  Math.round(
    (Date.parse(end + "T00:00:00Z") - Date.parse(start + "T00:00:00Z")) /
      86400000,
  );
export const localDate = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
