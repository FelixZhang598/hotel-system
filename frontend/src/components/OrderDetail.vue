<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useAuthStore } from "../stores/auth";
import * as guestApi from "../api/orders";
import * as deskApi from "../api/frontdesk";
import { errorMessage } from "../api/http";
import { statusLabels, statusType, money, time, nights } from "../format";
import type { Order, Room } from "../types";
const props = defineProps<{ frontdesk?: boolean }>(),
  route = useRoute(),
  store = useAuthStore();
const order = ref<Order | null>(null),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  showCheckIn = ref(false),
  candidates = ref<Room[]>([]),
  roomsLoading = ref(false),
  checkInError = ref(""),
  roomId = ref<number>(),
  guestName = ref(""),
  guestPhone = ref("");
const today = computed(() => store.hotel?.today || "");
const canCancel = computed(
  () =>
    order.value?.status === "CONFIRMED" &&
    !!today.value &&
    (props.frontdesk
      ? today.value <= order.value.checkIn
      : today.value < order.value.checkIn),
);
const canCheckIn = computed(
  () =>
    props.frontdesk &&
    order.value?.status === "CONFIRMED" &&
    today.value === order.value.checkIn,
);
const canNoShow = computed(
  () =>
    props.frontdesk &&
    order.value?.status === "CONFIRMED" &&
    today.value > order.value.checkIn,
);
const canCheckOut = computed(
  () =>
    props.frontdesk &&
    order.value?.status === "CHECKED_IN" &&
    today.value >= order.value.checkOut,
);
const isOverdue = computed(
  () =>
    order.value?.status === "CHECKED_IN" && today.value > order.value.checkOut,
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    await store.refreshMeta();
    order.value = await (
      props.frontdesk ? deskApi.getOrder : guestApi.getOrder
    )(Number(route.params.id));
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
async function perform(action: "cancel" | "no-show" | "check-out") {
  if (!order.value) return;
  const labels = {
    cancel: "取消预订",
    "no-show": "标记未到店",
    "check-out": "办理退房",
  };
  try {
    await ElMessageBox.confirm(
      action === "check-out"
        ? "确认客人已离店？退房后客房将标记为待清洁。"
        : action === "no-show"
          ? "确认客人未到店？将释放尚未使用的库存，订单不可恢复。"
          : "确认取消这笔预订？取消后订单不可恢复。",
      labels[action],
      {
        confirmButtonText: "确认" + labels[action],
        cancelButtonText: "返回",
        type: "warning",
      },
    );
  } catch {
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    order.value = props.frontdesk
      ? await deskApi.action(order.value.id, action)
      : await guestApi.cancelOrder(order.value.id);
    await store.refreshMeta();
    ElMessage.success(labels[action] + "成功");
  } catch (e) {
    error.value = errorMessage(e);
    const latest = await (
      props.frontdesk ? deskApi.getOrder : guestApi.getOrder
    )(Number(route.params.id)).catch(() => null);
    if (latest) order.value = latest;
  } finally {
    busy.value = false;
  }
}
async function loadRooms() {
  if (!order.value) return;
  roomsLoading.value = true;
  try {
    candidates.value = await deskApi.availableRooms(order.value.id);
    if (!candidates.value.some((r) => r.id === roomId.value))
      roomId.value = undefined;
  } catch (e) {
    checkInError.value = errorMessage(e);
    candidates.value = [];
  } finally {
    roomsLoading.value = false;
  }
}
async function openCheckIn() {
  showCheckIn.value = true;
  checkInError.value = "";
  guestName.value = "";
  guestPhone.value = "";
  roomId.value = undefined;
  await loadRooms();
}
async function submitCheckIn() {
  if (!order.value) return;
  checkInError.value = "";
  if (!roomId.value) {
    checkInError.value = "请选择可入住客房。";
    return;
  }
  const name = guestName.value.trim(),
    phone = guestPhone.value.trim();
  if (!name || name.length > 80) {
    checkInError.value = "入住人姓名须为 1–80 个字符。";
    return;
  }
  if (phone.length < 6 || phone.length > 30 || !/^[-+\d ]+$/.test(phone)) {
    checkInError.value =
      "联系电话须为 6–30 个字符，仅支持数字、+、空格和短横线。";
    return;
  }
  busy.value = true;
  try {
    order.value = await deskApi.checkIn(order.value.id, {
      roomId: roomId.value,
      guestName: name,
      guestPhone: phone,
    });
    showCheckIn.value = false;
    ElMessage.success("入住办理成功，祝客人入住愉快。");
  } catch (e) {
    checkInError.value = errorMessage(e);
    await loadRooms();
    const latest = await deskApi.getOrder(order.value.id).catch(() => null);
    if (latest) order.value = latest;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
watch(() => route.params.id, load);
</script>
<template>
  <div class="order-detail" :class="{ 'guest-content': !frontdesk }">
    <RouterLink
      :to="frontdesk ? '/frontdesk/orders' : '/orders'"
      class="back-link"
      >← {{ frontdesk ? "返回订单列表" : "返回我的旅程" }}</RouterLink
    ><el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="space-bottom"
    />
    <div v-loading="loading" class="detail-loading">
      <template v-if="order"
        ><div class="page-heading">
          <div>
            <span class="eyebrow">{{
              frontdesk ? "RESERVATION DETAIL" : "YOUR RESERVATION"
            }}</span>
            <h1>{{ order.roomTypeName }}</h1>
            <p>
              订单 {{ order.orderNo }}
              <span class="detail-date"
                >· {{ time(order.createdAt) }} 预订</span
              >
            </p>
          </div>
          <el-tag
            :type="statusType[order.status]"
            effect="light"
            size="large"
            >{{ statusLabels[order.status] }}</el-tag
          >
        </div>
        <el-alert
          v-if="isOverdue"
          title="逾期未退房：客房仍处于实际占用状态，请及时联系客人并办理退房。"
          type="warning"
          show-icon
          :closable="false"
          class="space-bottom" /><el-alert
          v-if="canNoShow"
          title="该订单已超过入住日期，无法办理入住。请核实后标记未到店。"
          type="warning"
          show-icon
          :closable="false"
          class="space-bottom" />
        <div class="detail-grid">
          <section class="data-panel stay-details">
            <div class="panel-heading">
              <h3>住宿安排</h3>
              <span class="eyebrow">STAY DETAILS</span>
            </div>
            <div class="detail-stay-dates">
              <div>
                <small>入住日期</small><strong>{{ order.checkIn }}</strong>
              </div>
              <span class="night-chip"
                >{{ nights(order.checkIn, order.checkOut) }} 晚</span
              >
              <div>
                <small>离店日期</small><strong>{{ order.checkOut }}</strong>
              </div>
            </div>
            <dl class="detail-dl">
              <div>
                <dt>预订房型</dt>
                <dd>{{ order.roomTypeName }}</dd>
              </div>
              <div>
                <dt>入住人数</dt>
                <dd>{{ order.guestCount }} 人 · 1 间客房</dd>
              </div>
              <div>
                <dt>每晚房价</dt>
                <dd>{{ money(order.nightlyPriceCents) }}</dd>
              </div>
              <div>
                <dt>房间安排</dt>
                <dd>
                  {{
                    order.stay
                      ? order.stay.roomNumber + " 号客房"
                      : "抵达时由前台安排"
                  }}
                </dd>
              </div>
            </dl>
            <div v-if="order.stay" class="stay-record">
              <h3>入住信息</h3>
              <dl class="detail-dl">
                <div>
                  <dt>入住人</dt>
                  <dd>{{ order.stay.guestName }}</dd>
                </div>
                <div>
                  <dt>联系电话</dt>
                  <dd>{{ order.stay.guestPhone }}</dd>
                </div>
                <div>
                  <dt>实际入住</dt>
                  <dd>{{ time(order.stay.checkedInAt) }}</dd>
                </div>
                <div v-if="order.stay.checkedOutAt">
                  <dt>实际离店</dt>
                  <dd>{{ time(order.stay.checkedOutAt) }}</dd>
                </div>
              </dl>
            </div>
          </section>
          <section class="data-panel order-summary">
            <span class="eyebrow">RESERVATION SUMMARY</span>
            <h3>订单概览</h3>
            <div class="summary-total">
              <small>住宿总金额</small
              ><strong>{{ money(order.totalCents) }}</strong
              ><span
                >{{ nights(order.checkIn, order.checkOut) }} 晚 ·
                {{ order.guestCount }} 人</span
              >
            </div>
            <p class="muted small">
              已确认的预订无需支付。<br />日期及操作条件以酒店时间为准。
            </p>
            <div class="detail-actions">
              <el-button
                v-if="canCheckIn"
                type="primary"
                size="large"
                :loading="busy"
                @click="openCheckIn"
                >办理入住 →</el-button
              ><el-button
                v-if="canCheckOut"
                type="primary"
                size="large"
                :loading="busy"
                @click="perform('check-out')"
                >办理退房</el-button
              ><el-button
                v-if="canCancel"
                size="large"
                :disabled="busy"
                @click="perform('cancel')"
                >取消预订</el-button
              ><el-button
                v-if="canNoShow"
                type="warning"
                size="large"
                :disabled="busy"
                @click="perform('no-show')"
                >标记未到店</el-button
              >
            </div>
            <p
              v-if="order.status === 'CONFIRMED' && !frontdesk && !canCancel"
              class="action-note"
            >
              入住日及之后无法自行取消，请联系酒店前台。
            </p>
            <p
              v-if="frontdesk && order.status === 'CHECKED_IN' && !canCheckOut"
              class="action-note"
            >
              计划离店日为 {{ order.checkOut }}，尚未到可退房日期。
            </p>
            <p
              v-if="
                frontdesk &&
                order.status === 'CONFIRMED' &&
                today < order.checkIn
              "
              class="action-note"
            >
              入住当天可办理入住，届时选择具体房间。
            </p>
            <el-button link type="primary" @click="load"
              >刷新最新状态</el-button
            >
          </section>
        </div>
        <section class="data-panel timeline-panel">
          <div class="panel-heading">
            <h3>旅程记录</h3>
            <span class="muted small">酒店时间 · Asia/Shanghai</span>
          </div>
          <el-timeline
            ><el-timeline-item
              v-for="(event, index) in order.events"
              :key="index"
              :timestamp="time(event.createdAt)"
              placement="top"
              color="#315b4e"
              ><strong>{{ statusLabels[event.toStatus] }}</strong>
              <p class="muted">{{ event.reason }}</p></el-timeline-item
            ></el-timeline
          ><el-empty
            v-if="!order.events.length"
            description="暂无操作记录"
            :image-size="60"
          /></section></template
      ><el-empty v-else-if="!loading" description="无法加载此订单"
        ><el-button @click="load">重新加载</el-button></el-empty
      >
    </div>
    <el-dialog
      v-model="showCheckIn"
      title="为客人办理入住"
      width="560px"
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      ><p class="muted">
        {{ order?.roomTypeName }} · {{ order?.checkIn }} 至
        {{ order?.checkOut }}
      </p>
      <el-alert
        v-if="checkInError"
        :title="checkInError"
        type="error"
        show-icon
        :closable="false"
        class="space-bottom"
      /><el-alert
        v-if="!roomsLoading && !candidates.length && !checkInError"
        title="暂无可入住客房，请先完成清洁或处理仍占用的客房，再刷新候选房间。"
        type="warning"
        show-icon
        :closable="false"
        class="space-bottom"
      /><el-form label-position="top"
        ><el-form-item label="分配客房"
          ><el-select
            v-model="roomId"
            placeholder="选择已清洁的可用客房"
            :loading="roomsLoading"
            :disabled="busy"
            class="full-width"
            ><el-option
              v-for="room in candidates"
              :key="room.id"
              :value="room.id"
              :label="
                room.roomNumber + ' 号 · ' + room.roomTypeName
              " /></el-select
          ><el-button link type="primary" :disabled="busy" @click="loadRooms"
            >刷新候选房间</el-button
          ></el-form-item
        ><el-form-item label="入住人姓名"
          ><el-input
            v-model="guestName"
            placeholder="请输入入住人姓名"
            maxlength="80"
            :disabled="busy" /></el-form-item
        ><el-form-item label="联系电话"
          ><el-input
            v-model="guestPhone"
            placeholder="例如 13800138000"
            maxlength="30"
            :disabled="busy" /></el-form-item
      ></el-form>
      <p class="small muted">
        候选房间会在提交时再次校验；入住信息仅用于演示登记。
      </p>
      <template #footer
        ><el-button :disabled="busy" @click="showCheckIn = false"
          >返回</el-button
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="!candidates.length || roomsLoading"
          @click="submitCheckIn"
          >确认入住</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
