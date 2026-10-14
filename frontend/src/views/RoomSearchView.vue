<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../stores/auth";
import { searchRooms } from "../api/catalog";
import { createOrder } from "../api/orders";
import { ApiError, errorMessage } from "../api/http";
import { money, nights, addDays, localDate } from "../format";
import type { RoomType, BookingPayload } from "../types";
const store = useAuthStore(),
  route = useRoute(),
  router = useRouter();
const dateRange = ref<[string, string] | null>(null),
  searchedDates = ref<[string, string] | null>(null),
  roomTypes = ref<RoomType[]>([]),
  loading = ref(false),
  error = ref(""),
  selected = ref<RoomType | null>(null),
  guestCount = ref(1),
  dialog = ref(false),
  booking = ref(false),
  bookingError = ref(""),
  uncertain = ref(false);
const stayNights = computed(() =>
  searchedDates.value ? nights(...searchedDates.value) : 0,
);
function disabledDate(date: Date) {
  const day = localDate(date);
  return (
    !!store.hotel && (day < store.hotel.today || day > store.hotel.availableTo)
  );
}
async function load() {
  error.value = "";
  loading.value = true;
  try {
    if (!store.hotel) await store.refreshMeta();
    if (!dateRange.value)
      dateRange.value = [store.hotel!.today, addDays(store.hotel!.today, 1)];
    const [start, end] = dateRange.value;
    if (
      start < store.hotel!.today ||
      end <= start ||
      end > store.hotel!.availableTo
    ) {
      throw new Error("请选择可售范围内的入住与离店日期，至少入住一晚。");
    }
    roomTypes.value = await searchRooms(start, end);
    searchedDates.value = [start, end];
    await router.replace({
      path: "/",
      query: { checkIn: start, checkOut: end },
    });
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
function choose(room: RoomType) {
  selected.value = room;
  guestCount.value = 1;
  bookingError.value = "";
  uncertain.value = false;
  if (!store.user) {
    ElMessage.info("登录住客账号后即可确认预订。");
    router.push({
      path: "/login",
      query: {
        redirect:
          "/?" +
          new URLSearchParams({
            checkIn: searchedDates.value![0],
            checkOut: searchedDates.value![1],
            room: String(room.id),
          }),
      },
    });
    return;
  }
  if (store.user.role !== "GUEST") {
    ElMessage.warning("请使用住客账号预订客房。");
    return;
  }
  dialog.value = true;
}
async function book() {
  if (!selected.value || !searchedDates.value) return;
  booking.value = true;
  bookingError.value = "";
  const payload: BookingPayload = {
    roomTypeId: selected.value.id,
    checkIn: searchedDates.value[0],
    checkOut: searchedDates.value[1],
    guestCount: guestCount.value,
  };
  const storageKey = "qian-booking-" + store.user!.id;
  let snapshot: { key: string; payload: BookingPayload } | null = null;
  try {
    snapshot = JSON.parse(sessionStorage.getItem(storageKey) || "null");
  } catch {}
  if (!snapshot || JSON.stringify(snapshot.payload) !== JSON.stringify(payload))
    snapshot = { key: crypto.randomUUID(), payload };
  sessionStorage.setItem(storageKey, JSON.stringify(snapshot));
  try {
    const order = await createOrder(snapshot.payload, snapshot.key);
    sessionStorage.removeItem(storageKey);
    dialog.value = false;
    ElMessage.success("预订已确认，期待您的到来。");
    await router.push("/orders/" + order.id);
  } catch (e) {
    uncertain.value =
      e instanceof ApiError && (e.status === 0 || e.status >= 500);
    bookingError.value =
      errorMessage(e) +
      (uncertain.value
        ? " 已保留本次请求，重试将使用同一预订凭据，避免重复下单。"
        : "");
    if (e instanceof ApiError && e.code === "INVENTORY_INSUFFICIENT") {
      roomTypes.value = await searchRooms(...searchedDates.value).catch(
        () => roomTypes.value,
      );
    }
  } finally {
    booking.value = false;
  }
}
onMounted(async () => {
  const requestedRoom = Number(route.query.room);
  if (
    typeof route.query.checkIn === "string" &&
    typeof route.query.checkOut === "string"
  )
    dateRange.value = [route.query.checkIn, route.query.checkOut];
  await load();
  if (requestedRoom && store.user?.role === "GUEST") {
    const room = roomTypes.value.find((r) => r.id === requestedRoom);
    if (room && room.availableRooms > 0) choose(room);
  }
});
</script>
<template>
  <div class="room-search">
    <section class="hero">
      <div class="hero-content">
        <span class="eyebrow">YOUR NEXT QUIET ESCAPE</span>
        <h1>栖于自在，<br />岸边有你。</h1>
        <p>把匆忙留在门外。<br />在栖岸，找到属于自己的片刻安宁。</p>
        <div class="hero-caption">
          <span class="line"></span>舒适客房 · 从容入住 · 恰好相逢
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="art-window">
          <div class="art-moon"></div>
          <div class="art-hill hill-back"></div>
          <div class="art-hill hill-front"></div>
          <div class="art-water"></div>
          <div class="window-frame"></div>
        </div>
        <div class="art-plinth"></div>
        <div class="art-caption">
          STAY CLOSE TO SERENITY <span>01 / QIAN</span>
        </div>
      </div>
    </section>
    <section class="search-section">
      <div class="search-label">
        <span class="eyebrow">PLAN YOUR STAY</span>
        <h3>一段旅程，从选好日期开始</h3>
      </div>
      <div class="search-controls">
        <div>
          <label>入住日期 — 离店日期</label
          ><el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            format="MM 月 DD 日"
            start-placeholder="入住日期"
            end-placeholder="离店日期"
            range-separator="→"
            :disabled-date="disabledDate"
            :clearable="false"
            :editable="false"
            size="large"
          />
        </div>
        <el-button type="primary" size="large" :loading="loading" @click="load"
          >查询客房 <span class="button-arrow">↗</span></el-button
        >
      </div>
      <p v-if="store.hotel" class="range-note">
        可预订 {{ store.hotel.today }} 至 {{ store.hotel.availableTo }} 离店 ·
        日期以酒店时间为准
      </p>
    </section>
    <section class="rooms-section">
      <div class="section-heading">
        <div>
          <span class="eyebrow">ROOMS & SUITES</span>
          <h2>选一间，安放好时光</h2>
        </div>
        <span v-if="searchedDates" class="muted"
          >{{ searchedDates[0] }} — {{ searchedDates[1] }} ·
          {{ stayNights }} 晚</span
        >
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="space-bottom"
      />
      <div v-loading="loading" class="room-grid">
        <article
          v-for="(room, index) in roomTypes"
          :key="room.id"
          class="room-card"
        >
          <div class="room-illustration" :class="'room-scene-' + (index % 3)">
            <span class="room-number">0{{ index + 1 }}</span
            ><span class="room-scene-label">QIAN SIGNATURE ROOM</span>
            <div class="scene-window"></div>
            <div class="scene-light"></div>
            <div class="scene-bed"><i></i><i></i></div>
            <div class="scene-table"></div>
          </div>
          <div class="room-card-body">
            <div class="room-card-heading">
              <h3>{{ room.name }}</h3>
              <span class="capacity">{{ room.capacity }} 人入住</span>
            </div>
            <p>{{ room.description }}</p>
            <div class="room-amenities">
              <span>舒适床品</span><span>独立卫浴</span><span>静谧空间</span>
            </div>
            <div class="room-card-bottom">
              <div class="room-price">
                {{ money(room.nightlyPriceCents) }}<small> / 晚</small
                ><span v-if="stayNights"
                  >{{ stayNights }} 晚共
                  {{ money(room.nightlyPriceCents * stayNights) }}</span
                >
              </div>
              <el-button
                :disabled="room.availableRooms === 0 || !searchedDates"
                type="primary"
                @click="choose(room)"
                >{{
                  room.availableRooms > 0 ? "预订此房型" : "暂时满房"
                }}</el-button
              >
            </div>
            <div
              class="availability"
              :class="{ soldout: room.availableRooms === 0 }"
            >
              <i></i
              >{{
                room.availableRooms > 0
                  ? `本时段可售 ${room.availableRooms} 间`
                  : "所选时段已售罄"
              }}
            </div>
          </div>
        </article>
      </div>
      <el-empty
        v-if="!loading && !error && !roomTypes.length"
        description="这个时段暂无房型，试试其他日期。"
      />
      <div class="stay-note">
        <span>◇</span>
        <p>
          预订的是房型，具体房号在抵达时由前台为您安排。<br /><small
            >预订成功即确认，无需支付；入住日前可在我的旅程中取消。</small
          >
        </p>
      </div>
    </section>
    <el-dialog
      v-model="dialog"
      title="确认您的栖岸之旅"
      width="520px"
      :close-on-click-modal="!booking"
      :close-on-press-escape="!booking"
      :show-close="!booking"
      class="booking-dialog"
      ><template v-if="selected && searchedDates"
        ><span class="eyebrow">RESERVATION DETAILS</span>
        <h2>{{ selected.name }}</h2>
        <div class="booking-dates">
          <div>
            <small>入住</small><strong>{{ searchedDates[0] }}</strong>
          </div>
          <span>→</span>
          <div>
            <small>离店</small><strong>{{ searchedDates[1] }}</strong>
          </div>
        </div>
        <el-form label-position="top"
          ><el-form-item label="入住人数"
            ><el-input-number
              v-model="guestCount"
              :min="1"
              :max="selected.capacity"
              :disabled="booking"
            /><span class="muted form-hint"
              >最多 {{ selected.capacity }} 人</span
            ></el-form-item
          ></el-form
        >
        <div class="booking-total">
          <span
            >{{ stayNights }} 晚 × {{ money(selected.nightlyPriceCents) }}</span
          ><strong>{{ money(selected.nightlyPriceCents * stayNights) }}</strong>
        </div>
        <el-alert
          v-if="bookingError"
          :title="bookingError"
          type="error"
          show-icon
          :closable="false"
        />
        <p class="muted small">
          一间客房 · 无需支付 · 入住日前可取消
        </p></template
      ><template #footer
        ><el-button :disabled="booking" @click="dialog = false"
          >稍后决定</el-button
        ><el-button type="primary" :loading="booking" @click="book">{{
          uncertain ? "重试本次预订" : "确认预订"
        }}</el-button></template
      ></el-dialog
    >
  </div>
</template>
