<template>
  <div class="observability-grid">
    <section
      v-for="group in groups"
      :key="group.key || group.title"
      class="workspace-section observability-group"
    >
      <header class="section-header">
        <div>
          <h3 class="section-title">{{ group.title }}</h3>
          <p v-if="group.description" class="section-description">
            {{ group.description }}
          </p>
        </div>
        <span class="badge badge-neutral">
          {{ group.panels?.length || 0 }} Panels
        </span>
      </header>

      <div
        class="observability-grid__panels"
        :style="panelLayout(group.columns)"
      >
        <ObservabilityPanel
          v-for="panel in group.panels"
          :key="panel.key || `${group.key || group.title}-${panel.title}`"
          v-bind="panel"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import ObservabilityPanel from "./ObservabilityPanel.vue";

defineProps({
  groups: {
    type: Array,
    default: () => [],
  },
});

function panelLayout(columns) {
  return {
    gridTemplateColumns: `repeat(${columns || 2}, minmax(0, 1fr))`,
  };
}
</script>
