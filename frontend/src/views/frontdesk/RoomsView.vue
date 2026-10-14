<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { rooms, clean } from "../../api/frontdesk";
import { errorMessage } from "../../api/http";
import { useAuthStore } from "../../stores/auth";
import type { Room } from "../../types";
const store = useAuthStore(),
  items = ref<Room[]>([]),
  loading = ref(false),
  error = ref(""),
  cleaning = ref<number>(),
  filter = ref("all");
const counts = computed(() => ({
  all: items.value.length,
  available: items.value.filter(
    (r) => !r.occupied && r.cleaningStatus === "CLEAN",
  ).length,
  occupied: items.value.filter((r) => r.occupied).length,
  dirty: items.value.filter((r) => !r.occupied && r.cleaningStatus === "DIRTY")
    .length,
  overdue: items.value.filter((r) => r.overdue).length,
}));
const filtered = computed(() =>
  items.value.filter(
    (r) =>
      filter.value === "all" ||
      (filter.value === "available" &&
        !r.occupied &&
        r.cleaningStatus === "CLEAN") ||
      (filter.value === "occupied" && r.occupied) ||
      (filter.value === "dirty" &&
        !r.occupied &&
        r.cleaningStatus === "DIRTY") ||
      (filter.value === "overdue" && r.overdue),
  ),
);
const groups = computed(() => {
  const map = new Map<string, Room[]>();
  for (const room of filtered.value) {
    const group = map.get(room.roomTypeName) || [];
    group.push(room);
    map.set(room.roomTypeName, group);
  }
  return Array.from(map.entries());
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    await store.refreshMeta();
    items.value = await rooms();
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
async function finish(room: Room) {
  try {
    await ElMessageBox.confirm(
      "确认 " + room.roomNumber + " 号客房已完成清洁？完成后可供前台分配入住。",
      "确认清洁完成",
      { confirmButtonText: "确认已清洁", cancelButtonText: "返回" },
    );
  } catch {
    return;
  }
  cleaning.value = room.id;
  try {
    const updated = await clean(room.id);
    items.value = items.value.map((r) => (r.id === updated.id ? updated : r));
    ElMessage.success(room.roomNumber + " 号客房已清洁");
  } catch (e) {
    error.value = errorMessage(e);
    items.value = await rooms().catch(() => items.value);
  } finally {
    cleaning.value = undefined;
  }
}
onMounted(load);
</script>
<template>
  <div class="rooms-view">
    <div class="page-heading">
      <div>
        <span class="eyebrow">HOUSEKEEPING & ROOM STATUS</span>
        <h1>客房状态</h1>
        <p>准备好每一间客房，迎接下一位旅人。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新房态</el-button>
    </div>
    <div class="room-stats">
      <button :class="{ selected: filter === 'all' }" @click="filter = 'all'">
        <span>全部客房</span><strong>{{ counts.all }}</strong
        ><small>ROOMS TOTAL</small></button
      ><button
        :class="{ selected: filter === 'available' }"
        @click="filter = 'available'"
      >
        <span><i class="stat-dot available"></i>干净空房</span
        ><strong>{{ counts.available }}</strong
        ><small>READY FOR ARRIVAL</small></button
      ><button
        :class="{ selected: filter === 'occupied' }"
        @click="filter = 'occupied'"
      >
        <span><i class="stat-dot occupied"></i>已占用</span
        ><strong>{{ counts.occupied }}</strong
        ><small>GUESTS IN HOUSE</small></button
      ><button
        :class="{ selected: filter === 'dirty' }"
        @click="filter = 'dirty'"
      >
        <span><i class="stat-dot dirty"></i>待清洁空房</span
        ><strong>{{ counts.dirty }}</strong
        ><small>HOUSEKEEPING NEEDED</small>
      </button>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="space-bottom"
    /><el-alert
      v-if="counts.overdue"
      :title="`${counts.overdue} 间客房逾期未退房，仍处于占用状态，请优先处理。`"
      type="warning"
      show-icon
      :closable="false"
      class="space-bottom"
      ><el-button link type="warning" @click="filter = 'overdue'"
        >查看逾期客房</el-button
      ></el-alert
    >
    <div class="rooms-legend">
      <div>
        <span class="eyebrow">LIVE ROOM BOARD</span>
        <p>
          {{ store.hotel?.today }} · 酒店日期
          <span>实际占用与清洁状态分别展示</span>
        </p>
      </div>
      <el-button v-if="filter !== 'all'" link @click="filter = 'all'"
        >显示全部客房</el-button
      >
    </div>
    <div v-loading="loading" class="room-board">
      <section v-for="[name, group] in groups" :key="name" class="room-group">
        <h3>
          {{ name }}<span>{{ group.length }} 间</span>
        </h3>
        <div class="room-status-grid">
          <article
            v-for="room in group"
            :key="room.id"
            class="room-status-card"
            :class="{
              occupied: room.occupied,
              dirty: !room.occupied && room.cleaningStatus === 'DIRTY',
              overdue: room.overdue,
            }"
          >
            <div class="room-status-top">
              <strong>{{ room.roomNumber }}</strong
              ><el-tag
                :type="
                  room.occupied
                    ? 'info'
                    : room.cleaningStatus === 'CLEAN'
                      ? 'success'
                      : 'warning'
                "
                size="small"
                >{{ room.occupied ? "已占用" : "空房" }}</el-tag
              >
            </div>
            <div class="room-status-lines">
              <span>清洁状态</span
              ><span :class="{ warning: room.cleaningStatus === 'DIRTY' }">{{
                room.cleaningStatus === "CLEAN" ? "已清洁" : "待清洁"
              }}</span>
            </div>
            <div class="room-status-lines">
              <span>占用订单</span
              ><strong>{{ room.currentOrderNo || "—" }}</strong>
            </div>
            <div class="room-status-action">
              <span v-if="room.overdue" class="overdue-text">⚠ 逾期未退房</span
              ><span v-else-if="room.occupied" class="muted small"
                >客人入住中</span
              ><span
                v-else-if="room.cleaningStatus === 'CLEAN'"
                class="ready-text"
                >● 已就绪</span
              ><el-button
                v-else
                type="primary"
                plain
                size="small"
                :loading="cleaning === room.id"
                :disabled="cleaning !== undefined"
                @click="finish(room)"
                >确认清洁完成</el-button
              >
            </div>
          </article>
        </div>
      </section>
      <el-empty
        v-if="!loading && !groups.length"
        description="当前筛选下暂无客房"
      />
    </div>
  </div>
</template>
