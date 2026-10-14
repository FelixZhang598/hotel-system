import { request, refreshCsrf } from "./http";
import type { User, HotelMeta } from "../types";
export const me = () => request<User>("/auth/me");
export const meta = () => request<HotelMeta>("/meta");
export async function login(username: string, password: string) {
  await refreshCsrf();
  const user = await request<User>("/auth/login", {
    method: "POST",
    body: new URLSearchParams({ username, password }),
  });
  await refreshCsrf();
  return user;
}
export async function logout() {
  await request<void>("/auth/logout", { method: "POST" });
  await refreshCsrf();
}
