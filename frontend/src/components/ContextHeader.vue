<template>
  <header class="workspace-section context-header">
    <div class="context-header__main">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <div class="context-header__title-row">
        <h2>{{ title }}</h2>
        <div v-if="badges.length" class="context-header__badges">
          <span
            v-for="badge in badges"
            :key="badge.label"
            class="badge"
            :class="badgeClass(badge)"
          >
            {{ badge.label }}
          </span>
        </div>
      </div>
      <p v-if="description" class="context-header__description">
        {{ description }}
      </p>
    </div>

    <div v-if="$slots.actions" class="context-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
const props = defineProps({
  eyebrow: {
    type: String,
    default: "",
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: "",
  },
  badges: {
    type: Array,
    default: () => [],
  },
});

function badgeClass(badge) {
  return badge?.tone ? `badge-${badge.tone}` : "badge-neutral";
}
</script>
