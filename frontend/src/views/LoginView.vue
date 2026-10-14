<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { errorMessage } from "../api/http";
import BrandMark from "../components/BrandMark.vue";
const route = useRoute(),
  router = useRouter(),
  store = useAuthStore();
const username = ref("guest1"),
  password = ref("Demo12345!"),
  loading = ref(false),
  error = ref(
    route.query.connection
      ? "暂时无法连接酒店服务，请确认后端已启动，然后登录重试。"
      : "",
  );
function account(name: string) {
  username.value = name;
  password.value = "Demo12345!";
  error.value = "";
}
async function submit() {
  if (!username.value.trim() || !password.value) {
    error.value = "请输入账号和密码。";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    await store.login(username.value.trim(), password.value);
    const redirect =
      typeof route.query.redirect === "string" &&
      route.query.redirect.startsWith("/") &&
      !route.query.redirect.startsWith("//")
        ? route.query.redirect
        : "";
    await router.push(
      redirect ||
        (store.user?.role === "FRONTDESK" ? "/frontdesk/orders" : "/"),
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <RouterLink to="/" class="brand"
        ><BrandMark /><span
          >栖岸酒店<small>QIAN · A QUIET STAY</small></span
        ></RouterLink
      >
      <div class="story-copy">
        <span class="eyebrow">A PLACE TO SLOW DOWN</span>
        <h1>让旅途慢下来，<br />让心安住。</h1>
        <p>一间恰好的客房，一段从容的时光。<br />下一次抵达，从这里开始。</p>
      </div>
      <div class="story-arch">
        <div class="arch-sun"></div>
        <div class="arch-land"></div>
      </div>
      <span class="story-bottom">栖居一隅 · 安然靠岸</span>
    </section>
    <section class="login-form-wrap">
      <RouterLink to="/" class="back-link">← 返回探索客房</RouterLink>
      <div class="login-form">
        <span class="eyebrow">WELCOME TO QIAN</span>
        <h2>欢迎回来</h2>
        <p class="muted">登录，继续您的栖岸旅程。</p>
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          show-icon
          :closable="false"
          class="space-bottom"
        /><el-form label-position="top" @submit.prevent="submit"
          ><el-form-item label="账号"
            ><el-input
              v-model="username"
              size="large"
              placeholder="请输入账号"
              autocomplete="username" /></el-form-item
          ><el-form-item label="密码"
            ><el-input
              v-model="password"
              type="password"
              show-password
              size="large"
              autocomplete="current-password" /></el-form-item
          ><el-button
            native-type="submit"
            type="primary"
            size="large"
            class="full-width"
            :loading="loading"
            >登录并继续 <span class="button-arrow">→</span></el-button
          ></el-form
        >
        <div class="demo-accounts">
          <div class="demo-title">
            体验账号 <span>统一密码 Demo12345!</span>
          </div>
          <div class="demo-buttons">
            <button
              type="button"
              :class="{ selected: username === 'guest1' }"
              @click="account('guest1')"
            >
              住客一 <small>guest1</small></button
            ><button
              type="button"
              :class="{ selected: username === 'guest2' }"
              @click="account('guest2')"
            >
              住客二 <small>guest2</small></button
            ><button
              type="button"
              :class="{ selected: username === 'frontdesk' }"
              @click="account('frontdesk')"
            >
              酒店前台 <small>frontdesk</small>
            </button>
          </div>
        </div>
        <p class="login-footnote">
          本系统用于酒店业务演示，所有入住信息均为虚构。
        </p>
      </div>
    </section>
  </div>
</template>
