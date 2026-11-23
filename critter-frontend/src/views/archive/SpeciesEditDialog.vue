<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { SPECIES_CATEGORY_OPTIONS } from '@/utils/domain'
import type { SpeciesView } from '@/api/types'

/** 物种新增/编辑：俗名唯一，其余字段可留空；只改名录，不做保护等级推断。 */
const props = defineProps<{
  modelValue: boolean
  species: SpeciesView | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({
  commonName: '',
  scientificName: '',
  category: '',
  protectionLevel: '',
  note: '',
})
const submitting = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.commonName = props.species?.commonName ?? ''
    form.scientificName = props.species?.scientificName ?? ''
    form.category = props.species?.category ?? ''
    form.protectionLevel = props.species?.protectionLevel ?? ''
    form.note = props.species?.note ?? ''
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!form.commonName.trim()) {
    ElMessage.warning('物种俗名必填')
    return
  }
  submitting.value = true
  try {
    const payload = {
      commonName: form.commonName.trim(),
      scientificName: form.scientificName.trim() || null,
      category: form.category || null,
      protectionLevel: form.protectionLevel.trim() || null,
      note: form.note.trim() || null,
    }
    if (props.species) {
      await archiveApi.updateSpecies(props.species.id, payload)
      ElMessage.success('物种信息已更新')
    } else {
      await archiveApi.createSpecies(payload)
      ElMessage.success('物种已加入名录')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="species ? '编辑物种' : '新增物种'"
    width="620px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div class="dialog-note">
      俗名重复会返回 30411；学名与保护级别按名录登记值保存，前端不做推断。
    </div>

    <div class="form-grid">
      <div class="field">
        <label>俗名 <b>*</b></label>
        <input v-model="form.commonName" class="input" maxlength="64" placeholder="例如：白头鹎" />
      </div>
      <div class="field">
        <label>学名</label>
        <input v-model="form.scientificName" class="input" maxlength="128" placeholder="例如：Pycnonotus sinensis" />
      </div>
      <div class="field">
        <label>类群</label>
        <select v-model="form.category" class="select">
          <option value="">未分类</option>
          <option v-for="item in SPECIES_CATEGORY_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>
      <div class="field">
        <label>保护级别</label>
        <input v-model="form.protectionLevel" class="input" maxlength="16" placeholder="例如：国家三有" />
      </div>
      <div class="field form-span-2">
        <label>备注</label>
        <textarea v-model="form.note" class="textarea" maxlength="255" placeholder="可不填" />
      </div>
    </div>

    <div v-if="errorText" class="notice">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '保存中…' : '保存' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-note {
  margin-bottom: 14px;
  padding: 9px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.form-span-2 {
  grid-column: span 2;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: span 1;
  }
}
</style>
