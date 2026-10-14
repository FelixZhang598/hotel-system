<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { errorMessage } from "../api/http";
import BrandMark from "../components/BrandMark.vue";
const store = useAuthStore(),
  router = useRouter();
async function logout() {
  try {
    await store.logout();
    router.push("/");
    ElMessage.success("已退出登录");
  } catch (e) {
    ElMessage.error(errorMessage(e));
  }
}
</script>
<template>
  <div class="guest-app">
    <header class="site-header">
      <RouterLink to="/" class="brand"
        ><BrandMark /><span
          >栖岸酒店<small>QIAN · A QUIET STAY</small></span
        ></RouterLink
      >
      <nav>
        <RouterLink to="/" :class="{ active: $route.path === '/' }"
          >探索客房</RouterLink
        ><RouterLink v-if="store.user?.role === 'GUEST'" to="/orders"
          >我的旅程</RouterLink
        ><RouterLink
          v-if="store.user?.role === 'FRONTDESK'"
          to="/frontdesk/orders"
          >前台工作台</RouterLink
        >
      </nav>
      <div class="header-account">
        <template v-if="store.user"
          ><span class="account-name">{{ store.user.username }}</span
          ><el-button text @click="logout">退出</el-button></template
        ><RouterLink v-else to="/login" class="login-link"
          >登录 / 开始旅程 <span>↗</span></RouterLink
        >
      </div>
    </header>
    <main><RouterView /></main>
    <footer class="site-footer">
      <div class="footer-brand">栖岸 · 在旅途里，留一处安静</div>
      <span>酒店时区 Asia/Shanghai · 演示系统，无需支付</span
      ><span>QIAN HOTEL © 2026</span>
    </footer>
  </div>
</template>
