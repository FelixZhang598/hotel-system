import { defineStore } from "pinia";
import { ref } from "vue";
import * as auth from "../api/auth";
import { ApiError } from "../api/http";
import type { User, HotelMeta } from "../types";
export const useAuthStore = defineStore("auth", () => {
  const user = ref<User | null>(null),
    hotel = ref<HotelMeta | null>(null),
    ready = ref(false);
  async function restore() {
    if (ready.value) return;
    try {
      user.value = await auth.me();
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
    } finally {
      ready.value = true;
    }
  }
  async function refreshMeta() {
    hotel.value = await auth.meta();
    return hotel.value;
  }
  async function login(username: string, password: string) {
    user.value = await auth.login(username, password);
    ready.value = true;
  }
  async function logout() {
    await auth.logout();
    user.value = null;
  }
  return { user, hotel, ready, restore, refreshMeta, login, logout };
});
