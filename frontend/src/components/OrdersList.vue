<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import * as guestApi from "../api/orders";
import * as deskApi from "../api/frontdesk";
import { errorMessage } from "../api/http";
import { statusLabels, statusType, money } from "../format";
import type { Order, OrderFilter } from "../types";
const props = defineProps<{ frontdesk?: boolean }>(),
  router = useRouter(),
  route = useRoute();
const filter = reactive<OrderFilter>({
  page: Math.max(1, Number(route.query.page) || 1),
  size: 20,
  status: typeof route.query.status === "string" ? route.query.status : "",
  orderNo: typeof route.query.orderNo === "string" ? route.query.orderNo : "",
  checkIn: typeof route.query.checkIn === "string" ? route.query.checkIn : "",
});
const items = ref<Order[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref("");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    await router.replace({
      path: route.path,
      query: {
        page: String(filter.page),
        ...(filter.status ? { status: filter.status } : {}),
        ...(filter.orderNo ? { orderNo: filter.orderNo } : {}),
        ...(filter.checkIn ? { checkIn: filter.checkIn } : {}),
      },
    });
    const result = await (
      props.frontdesk ? deskApi.listOrders : guestApi.listOrders
    )({ ...filter, orderNo: filter.orderNo?.trim() });
    items.value = result.items;
    total.value = result.total;
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
function search() {
  filter.page = 1;
  load();
}
function reset() {
  filter.status = "";
  filter.orderNo = "";
  filter.checkIn = "";
  search();
}
function rowClicked(row: Order) {
  detail(row.id);
}
function detail(id: number) {
  router.push((props.frontdesk ? "/frontdesk/orders/" : "/orders/") + id);
}
onMounted(load);
</script>
<template>
  <div class="orders-view" :class="{ 'guest-content': !frontdesk }">
    <div class="page-heading">
      <div>
        <span class="eyebrow">{{
          frontdesk ? "RESERVATIONS & ARRIVALS" : "YOUR STAYS AT QIAN"
        }}</span>
        <h1>{{ frontdesk ? "订单与入住" : "我的旅程" }}</h1>
        <p>
          {{
            frontdesk
              ? "从预订到离店，在这里照顾好每一次旅程。"
              : "每一次出发，都有一处栖岸等您抵达。"
          }}
        </p>
      </div>
      <RouterLink v-if="!frontdesk" to="/"
        ><el-button type="primary" size="large"
          >开启新旅程 ↗</el-button
        ></RouterLink
      ><el-button v-else @click="load" :loading="loading">刷新订单</el-button>
    </div>
    <section class="data-panel">
      <el-form class="filter-bar" @submit.prevent="search"
        ><el-form-item label="订单号"
          ><el-input
            v-model="filter.orderNo"
            placeholder="完整订单号"
            clearable /></el-form-item
        ><el-form-item label="订单状态"
          ><el-select v-model="filter.status" placeholder="全部状态" clearable
            ><el-option
              v-for="(label, value) in statusLabels"
              :key="value"
              :value="value"
              :label="label" /></el-select></el-form-item
        ><el-form-item label="入住日期"
          ><el-date-picker
            v-model="filter.checkIn"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            clearable
            :editable="false"
        /></el-form-item>
        <div class="filter-actions">
          <el-button type="primary" native-type="submit" :loading="loading"
            >查询</el-button
          ><el-button @click="reset">重置</el-button>
        </div></el-form
      ><el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="space-bottom"
      />
      <div class="table-heading">
        <h3>
          {{ frontdesk ? "全店预订" : "预订记录" }}<span>{{ total }} 条</span>
        </h3>
        <span class="muted small">按预订时间由新到旧</span>
      </div>
      <el-table
        :data="items"
        v-loading="loading"
        class="orders-table"
        :empty-text="error ? '加载失败，请重试' : '暂无订单记录'"
        @row-click="rowClicked"
        ><el-table-column label="订单 / 房型" min-width="210"
          ><template #default="{ row }"
            ><div class="table-order">{{ row.orderNo }}</div>
            <span class="muted"
              >{{ row.roomTypeName }} · {{ row.guestCount }} 人</span
            ></template
          ></el-table-column
        ><el-table-column label="入住 — 离店" min-width="220"
          ><template #default="{ row }"
            ><span
              >{{ row.checkIn }} <span class="date-arrow">→</span>
              {{ row.checkOut }}</span
            ></template
          ></el-table-column
        ><el-table-column label="订单金额" min-width="110"
          ><template #default="{ row }"
            ><strong class="table-money">{{
              money(row.totalCents)
            }}</strong></template
          ></el-table-column
        ><el-table-column label="状态" min-width="110"
          ><template #default="{ row }"
            ><el-tag
              :type="statusType[row.status as keyof typeof statusType]"
              effect="light"
              >{{
                statusLabels[row.status as keyof typeof statusLabels]
              }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column v-if="frontdesk" label="房号" min-width="90"
          ><template #default="{ row }">{{
            row.stay?.roomNumber || "待分配"
          }}</template></el-table-column
        ><el-table-column label="操作" width="100" align="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click.stop="detail(row.id)"
              >查看详情 ↗</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.page"
          :page-size="filter.size"
          :total="total"
          layout="prev, pager, next"
          @current-change="load"
        /><span class="muted small">共 {{ total }} 条记录</span>
      </div>
    </section>
    <div v-if="!frontdesk" class="guest-tip">
      预订的是房型，抵达后由前台分配具体客房。入住日前可取消已确认的订单。
    </div>
  </div>
</template>
