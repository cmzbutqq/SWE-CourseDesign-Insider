<template>
  <article class="observability-panel">
    <header class="observability-panel__header">
      <div>
        <h3 class="panel-title">{{ title }}</h3>
        <p v-if="description" class="panel-description">{{ description }}</p>
      </div>
      <span class="badge badge-neutral source-badge">{{ source }}</span>
    </header>

    <div class="observability-frame-shell" :style="frameStyle">
      <div class="observability-frame-viewport" :style="frameViewportStyle">
        <iframe
          class="observability-frame"
          :class="{ 'is-hidden': hasError }"
          :title="resolvedFrameTitle"
          :src="src"
          loading="lazy"
          @load="handleLoad"
          @error="handleError"
        />
      </div>

      <div v-if="isLoading" class="panel-loading">加载中...</div>

      <div v-if="hasError" class="panel-fallback">
        <p class="panel-fallback__title">
          {{ source }} 无法在当前工作台加载
        </p>
        <p class="panel-fallback__description">
          可以切换到原始页面继续排查嵌入限制或工具端状态。
        </p>
        <a
          v-if="fallbackHref"
          class="panel-link"
          :href="fallbackHref"
          target="_blank"
          rel="noopener"
        >
          打开原始页面
        </a>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, ref, watch } from "vue";

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: "",
  },
  source: {
    type: String,
    required: true,
  },
  src: {
    type: String,
    required: true,
  },
  fallbackHref: {
    type: String,
    default: "",
  },
  height: {
    type: [String, Number],
    default: "320px",
  },
  frameTitle: {
    type: String,
    default: "",
  },
  frameScale: {
    type: Number,
    default: 1,
  },
});

const isLoading = ref(true);
const hasError = ref(false);

const resolvedHeight = computed(() =>
  typeof props.height === "number" ? `${props.height}px` : props.height
);

const resolvedFrameScale = computed(() => {
  const scale = Number(props.frameScale);
  return Number.isFinite(scale) && scale > 0 && scale <= 1 ? scale : 1;
});

const frameStyle = computed(() => ({
  height: resolvedHeight.value,
  minHeight: resolvedHeight.value,
  "--observability-frame-scale": String(resolvedFrameScale.value),
}));

const frameViewportStyle = computed(() => ({
  width: `calc(100% / ${resolvedFrameScale.value})`,
  height: `calc(100% / ${resolvedFrameScale.value})`,
  transform: `scale(${resolvedFrameScale.value})`,
  transformOrigin: "top left",
}));

const resolvedFrameTitle = computed(
  () => props.frameTitle || `${props.title} - ${props.source}`
);

watch(
  () => props.src,
  () => {
    isLoading.value = true;
    hasError.value = false;
  }
);

function handleLoad() {
  if (!hasError.value) {
    isLoading.value = false;
  }
}

function handleError() {
  hasError.value = true;
  isLoading.value = false;
}
</script>
