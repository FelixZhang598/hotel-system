<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { ElMessage } from "element-plus";
import { useAuthStore } from "./stores/auth";
import { router } from "./router";
const store = useAuthStore();
function expired() {
  store.user = null;
  ElMessage.warning("登录已过期，请重新登录。");
  router.push({
    path: "/login",
    query: { redirect: router.currentRoute.value.fullPath },
  });
}
onMounted(() => window.addEventListener("session-expired", expired));
onUnmounted(() => window.removeEventListener("session-expired", expired));
</script>
<template><RouterView /></template>
