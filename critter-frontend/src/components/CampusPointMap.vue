<script setup lang="ts">
import { computed } from 'vue'
import type { PointView } from '@/api/types'

/**
 * 校园示意点位图。
 * 底图资产尚未提供，因此不伪造校园形状：按接口的 0–100 相对坐标落点，
 * 叠加网格与“示意”标注。等真实平面图到位后，只需替换 .map-canvas 的 background-image。
 */
const props = defineProps<{
  points: PointView[]
  modelValue: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
}>()

const areas = computed(() => {
  const groups = new Map<string, PointView[]>()
  for (const point of props.points.filter((item) => item.enabled)) {
    const list = groups.get(point.area) ?? []
    list.push(point)
    groups.set(point.area, list)
  }
  return [...groups.entries()].map(([area, points]) => ({ area, points }))
})

const selected = computed(() => props.points.find((point) => point.id === props.modelValue) ?? null)

function choose(point: PointView): void {
  emit('update:modelValue', props.modelValue === point.id ? null : point.id)
}
</script>

<template>
  <div class="point-picker">
    <div class="map-canvas" role="group" aria-label="校园示意点位图">
      <span class="map-watermark">示意点位图</span>
      <button
        v-for="point in points.filter((item) => item.enabled)"
        :key="point.id"
        class="map-dot"
        :class="{ selected: modelValue === point.id }"
        type="button"
        :style="{ left: `${point.x}%`, top: `${point.y}%` }"
        :title="`${point.name}（${point.area}）`"
        @click="choose(point)"
      >
        <i />
        <span>{{ point.name }}</span>
      </button>
    </div>

    <div class="point-legend">
      <div class="legend-head">
        <span>按区域选点</span>
        <b v-if="selected">{{ selected.name }}</b>
        <span v-else class="muted">未选择</span>
      </div>
      <div v-for="group in areas" :key="group.area" class="area-row">
        <span class="area-name">{{ group.area }}</span>
        <div class="area-points">
          <button
            v-for="point in group.points"
            :key="point.id"
            class="point-chip"
            :class="{ on: modelValue === point.id }"
            type="button"
            @click="choose(point)"
          >
            {{ point.name }}
          </button>
        </div>
      </div>
      <div class="map-note">坐标来自 <span class="mono">GET /points</span>，底图待校准；当前按相对坐标示意落点。</div>
    </div>
  </div>
</template>

<style scoped>
.point-picker {
  display: grid;
  grid-template-columns: minmax(260px, 1.15fr) 1fr;
  gap: 14px;
}

.map-canvas {
  position: relative;
  min-height: 300px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background-color: var(--chip);
  background-image:
    linear-gradient(to right, var(--line) 1px, transparent 1px),
    linear-gradient(to bottom, var(--line) 1px, transparent 1px),
    radial-gradient(circle at 50% 45%, var(--panel), transparent 62%);
  background-size: 10% 10%, 10% 10%, 100% 100%;
}

.map-watermark {
  position: absolute;
  right: 12px;
  bottom: 8px;
  color: var(--muted);
  opacity: 0.55;
  font-size: 11px;
  letter-spacing: 2px;
}

.map-dot {
  position: absolute;
  transform: translate(-50%, -50%);
  border: 0;
  background: transparent;
  padding: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  z-index: 1;
}

.map-dot i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  border: 2px solid var(--panel);
  background: var(--accent-2);
  box-shadow: 0 0 0 1px var(--line);
}

.map-dot span {
  max-width: 86px;
  padding: 1px 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--panel) 82%, transparent);
  color: var(--muted);
  font-size: 10px;
  line-height: 1.35;
  white-space: nowrap;
}

.map-dot:hover i,
.map-dot.selected i {
  width: 12px;
  height: 12px;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.map-dot.selected span {
  color: var(--accent);
  font-weight: 600;
  background: var(--accent-soft);
}

.point-legend {
  min-width: 0;
}

.legend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  color: var(--muted);
}

.legend-head b {
  color: var(--accent);
  font-size: 12px;
}

.area-row {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  align-items: flex-start;
}

.area-name {
  flex: 0 0 64px;
  color: var(--muted);
  font-size: 11px;
  padding-top: 4px;
}

.area-points {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.point-chip {
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 999px;
  padding: 3px 9px;
  font-size: 11px;
  color: var(--muted);
  cursor: pointer;
}

.point-chip:hover,
.point-chip.on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.map-note {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 1000px) {
  .point-picker {
    grid-template-columns: 1fr;
  }

  .map-canvas {
    min-height: 260px;
  }
}
</style>
