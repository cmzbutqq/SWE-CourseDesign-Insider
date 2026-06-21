<template>
  <section class="workspace-section risk-summary" :class="toneClass">
    <header class="section-header">
      <div>
        <h3 class="section-title">{{ title }}</h3>
        <p v-if="description" class="section-description">{{ description }}</p>
      </div>
      <span class="badge" :class="badgeClass">
        {{ items.length }} {{ variant === "list" ? "Items" : "Cards" }}
      </span>
    </header>

    <div v-if="items.length && variant === 'cards'" class="summary-grid">
      <article
        v-for="item in items"
        :key="item.label || item.title"
        class="summary-card"
        :class="itemToneClass(item)"
      >
        <div class="summary-card__header">
          <span class="summary-card__label">{{ item.label || item.title }}</span>
          <strong
            v-if="item.value !== undefined && item.value !== null && item.value !== ''"
            class="summary-card__value"
          >
            {{ item.value }}
          </strong>
        </div>
        <p v-if="item.detail" class="summary-card__detail">{{ item.detail }}</p>
      </article>
    </div>

    <ul v-else-if="items.length" class="summary-list">
      <li
        v-for="item in items"
        :key="item.label || item.title"
        class="summary-list__item"
        :class="itemToneClass(item)"
      >
        <strong class="summary-list__title">{{ item.title || item.label }}</strong>
        <p v-if="item.detail" class="summary-list__detail">{{ item.detail }}</p>
      </li>
    </ul>

    <p v-else class="muted">{{ emptyText }}</p>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: "",
  },
  items: {
    type: Array,
    default: () => [],
  },
  variant: {
    type: String,
    default: "cards",
  },
  tone: {
    type: String,
    default: "neutral",
  },
  emptyText: {
    type: String,
    default: "暂无异常或补充说明。",
  },
});

const toneClass = computed(() => `tone-${props.tone}`);
const badgeClass = computed(() => `badge-${props.tone}`);

function itemToneClass(item) {
  return item?.tone ? `tone-${item.tone}` : "";
}
</script>
