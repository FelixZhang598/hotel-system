import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("../views/LoginView.vue"),
    },
    {
      path: "/",
      component: () => import("../layouts/GuestLayout.vue"),
      children: [
        {
          path: "",
          name: "rooms",
          component: () => import("../views/RoomSearchView.vue"),
        },
        {
          path: "orders",
          name: "my-orders",
          component: () => import("../views/MyOrdersView.vue"),
          meta: { role: "GUEST" },
        },
        {
          path: "orders/:id",
          name: "my-order",
          component: () => import("../views/OrderDetailView.vue"),
          meta: { role: "GUEST" },
        },
      ],
    },
    {
      path: "/frontdesk",
      component: () => import("../layouts/FrontdeskLayout.vue"),
      meta: { role: "FRONTDESK" },
      children: [
        { path: "", redirect: "/frontdesk/orders" },
        {
          path: "orders",
          name: "desk-orders",
          component: () => import("../views/frontdesk/OrdersView.vue"),
        },
        {
          path: "orders/:id",
          name: "desk-order",
          component: () => import("../views/frontdesk/OrderDetailView.vue"),
        },
        {
          path: "rooms",
          name: "desk-rooms",
          component: () => import("../views/frontdesk/RoomsView.vue"),
        },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
router.beforeEach(async (to) => {
  const store = useAuthStore();
  try {
    await store.restore();
  } catch {
    return to.path === "/login"
      ? true
      : { path: "/login", query: { redirect: to.fullPath, connection: "1" } };
  }
  if (to.meta.role && !store.user)
    return { path: "/login", query: { redirect: to.fullPath } };
  if (to.meta.role && store.user?.role !== to.meta.role)
    return store.user?.role === "FRONTDESK" ? "/frontdesk/orders" : "/";
});
