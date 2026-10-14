<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import BrandMark from "../components/BrandMark.vue";
import { errorMessage } from "../api/http";
const store = useAuthStore(),
  router = useRouter();
async function logout() {
  try {
    await store.logout();
    router.push("/login");
  } catch (e) {
    ElMessage.error(errorMessage(e));
  }
}
</script>
<template>
  <div class="desk-app">
    <aside class="desk-sidebar">
      <RouterLink to="/frontdesk/orders" class="brand"
        ><BrandMark /><span>栖岸酒店<small>FRONT DESK</small></span></RouterLink
      >
      <div class="sidebar-label">酒店运营</div>
      <nav>
        <RouterLink to="/frontdesk/orders"><span>▤</span> 订单与入住</RouterLink
        ><RouterLink to="/frontdesk/rooms"><span>▦</span> 客房状态</RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="desk-note">每一次抵达<br />都值得认真相待。</div>
        <RouterLink to="/">↗ 查看住客预订页</RouterLink>
      </div>
    </aside>
    <div class="desk-workspace">
      <header class="desk-header">
        <span>前台工作台 <i></i> 单酒店管理</span>
        <div>
          <span>{{ store.user?.username }} · 前台</span
          ><el-button text @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="desk-main"><RouterView /></main>
    </div>
  </div>
</template>
