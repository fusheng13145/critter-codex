<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as userApi from '@/api/user'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import PanelCard from '@/components/PanelCard.vue'
import { actionLabel, dateTime, money, roleLabel, statusLabel } from '@/utils/format'
import { donationStatusLabel } from '@/utils/domain'
import type { ApplicationItem, DonationSummary, LoginLogItem, Profile } from '@/api/types'

/**
 * P11 个人中心：资料、改密、身份申请与登录日志。
 * 改密成功后旧令牌立即失效（服务端 min-iat 机制），所以这里主动退出并回登录页，
 * 而不是让用户在下一次请求时莫名收到 401。
 */

const auth = useAuthStore()
const router = useRouter()

const profile = ref<Profile | null>(null)
const applications = ref<ApplicationItem[]>([])
const loginLogs = ref<LoginLogItem[]>([])
const savingProfile = ref(false)
const savingPassword = ref(false)
const submittingApply = ref(false)
const errorText = ref('')
const donations = ref<DonationSummary[]>([])
const donationsLoading = ref(false)
const donationsError = ref('')
const certificateBusy = ref('')

const profileForm = reactive({ nickname: '', dept: '', phone: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const applyForm = reactive({ roleCode: 'VOLUNTEER', reason: '', availableSlots: '' })

/** 账号申诉：与登录页共用同一公开接口，这里方便登录用户对处理结果提出异议。 */
const appealForm = reactive({ reason: '', contact: '', message: '', submitted: false })
const submittingAppeal = ref(false)

const roleTags = computed(() => profile.value?.roles ?? [])

async function load(): Promise<void> {
  try {
    const data = await userApi.fetchProfile()
    profile.value = data
    profileForm.nickname = data.nickname
    profileForm.dept = data.dept ?? ''
    profileForm.phone = data.phone ?? ''
    applications.value = await userApi.fetchMyApplications()
    loginLogs.value = await userApi.fetchLoginLogs(10)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '个人资料加载失败'
  }
}

async function saveProfile(): Promise<void> {
  if (profileForm.nickname.trim().length < 2) {
    ElMessage.warning('昵称至少 2 个字')
    return
  }
  savingProfile.value = true
  try {
    const updated = await userApi.updateProfile({
      nickname: profileForm.nickname.trim(),
      dept: profileForm.dept.trim() || null,
      phone: profileForm.phone.trim() || null,
    })
    profile.value = updated
    ElMessage.success('资料已更新')
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '保存失败')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword(): Promise<void> {
  if (passwordForm.newPassword !== passwordForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (passwordForm.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位，且需含字母与数字')
    return
  }
  savingPassword.value = true
  try {
    await userApi.changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码已更新，请用新密码重新登录')
    await auth.logout()
    router.replace({ name: 'login' })
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '修改失败')
  } finally {
    savingPassword.value = false
  }
}

async function submitApply(): Promise<void> {
  if (applyForm.reason.trim().length < 10) {
    ElMessage.warning('申请理由不少于 10 个字')
    return
  }
  submittingApply.value = true
  try {
    await userApi.applyRole({
      roleCode: applyForm.roleCode,
      reason: applyForm.reason.trim(),
      availableSlots: applyForm.roleCode === 'VOLUNTEER' ? applyForm.availableSlots.trim() : undefined,
    })
    ElMessage.success('申请已提交，等待管理员审核')
    applyForm.reason = ''
    applyForm.availableSlots = ''
    applications.value = await userApi.fetchMyApplications()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '提交失败')
  } finally {
    submittingApply.value = false
  }
}

async function loadDonations(): Promise<void> {
  donationsLoading.value = true
  donationsError.value = ''
  try {
    const result = await fundApi.fetchMyDonations({ page: 1, size: 10 })
    donations.value = result.list
  } catch (error) {
    donationsError.value = error instanceof BizError ? error.message : '我的捐赠加载失败'
  } finally {
    donationsLoading.value = false
  }
}

async function downloadCertificate(row: DonationSummary): Promise<void> {
  if (certificateBusy.value) return
  certificateBusy.value = row.donationNo
  try {
    const blob = await fundApi.fetchDonationCertificate(row.donationNo)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `捐赠证书-${row.donationNo}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '证书下载失败')
  } finally {
    certificateBusy.value = ''
  }
}

async function confirmLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await auth.logout()
  router.replace({ name: 'login' })
}

async function submitAppeal(): Promise<void> {
  if (!profile.value) return
  if (appealForm.reason.trim().length < 5) {
    ElMessage.warning('请填写至少 5 个字的申诉理由')
    return
  }
  submittingAppeal.value = true
  try {
    const result = await userApi.submitAppeal({
      userNo: profile.value.userNo,
      reason: appealForm.reason.trim(),
      contact: appealForm.contact.trim() || undefined,
    })
    appealForm.submitted = true
    appealForm.message = result.message
    ElMessage.success('申诉已提交')
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '提交失败，请稍后重试')
  } finally {
    submittingAppeal.value = false
  }
}

onMounted(async () => {
  await load()
  await loadDonations()
})
</script>

<template>
  <div>
    <PageHero title="个人中心" desc="资料、密码与叠加身份申请都在这里；联系方式只对本人与管理员可见。">
      <template #actions>
        <button class="btn ghost" type="button" @click="confirmLogout">退出登录</button>
      </template>
    </PageHero>

    <div v-if="errorText" class="notice" style="margin-bottom: 16px">{{ errorText }}</div>

    <div class="card" style="margin-bottom: 16px">
      <div class="profile-card">
        <div class="avatar-lg">{{ auth.avatarText }}</div>
        <div class="grow">
          <h1 style="font-size: 22px">
            {{ profile?.nickname ?? '—' }}
            <span class="mono">{{ profile?.userNo }}</span>
          </h1>
          <p class="muted" style="margin: 4px 0">
            {{ profile?.dept ?? '未填写院系' }} · {{ profile?.email }}
          </p>
          <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px">
            <span v-for="role in roleTags" :key="role" class="tag">{{ roleLabel(role) }}</span>
            <span class="tag gray">{{ statusLabel(profile?.status) }}</span>
          </div>
        </div>
        <div class="muted" style="font-size: 12px; text-align: right">
          注册于<br />{{ dateTime(profile?.createdAt) }}
        </div>
      </div>
      <div class="info-grid">
        <div class="info"><div class="k">主角色</div><div class="v">{{ profile?.primaryRoleLabel ?? '—' }}</div></div>
        <div class="info"><div class="k">手机号（本人可见）</div><div class="v">{{ profile?.phone ?? '未填写' }}</div></div>
        <div class="info"><div class="k">脱敏展示</div><div class="v">{{ profile?.phoneMasked ?? '—' }}</div></div>
        <div class="info"><div class="k">账号状态</div><div class="v">{{ statusLabel(profile?.status) }}</div></div>
      </div>
    </div>

    <div class="grid2">
      <PanelCard title="修改资料" more="昵称会出现在公开页">
        <div class="field">
          <label>昵称</label>
          <input v-model="profileForm.nickname" class="input" maxlength="16" />
        </div>
        <div class="field">
          <label>院系</label>
          <input v-model="profileForm.dept" class="input" placeholder="例如 计算机学院" />
        </div>
        <div class="field">
          <label>手机号（可选，加密存储）</label>
          <input v-model="profileForm.phone" class="input" placeholder="11 位手机号" maxlength="11" />
        </div>
        <button class="btn" type="button" :disabled="savingProfile" @click="saveProfile">
          {{ savingProfile ? '保存中…' : '保存资料' }}
        </button>
        <div class="footnote" style="text-align: left">
          手机号在库内为 AES 密文，接口返回按角色脱敏；本人可见全量，其他人只看 138****2210。
        </div>
      </PanelCard>

      <PanelCard title="修改密码" more="改密后所有旧令牌立即失效">
        <div class="field">
          <label>原密码</label>
          <input v-model="passwordForm.oldPassword" class="input" type="password" autocomplete="current-password" />
        </div>
        <div class="field">
          <label>新密码（8—32 位，含字母与数字）</label>
          <input v-model="passwordForm.newPassword" class="input" type="password" autocomplete="new-password" />
        </div>
        <div class="field">
          <label>确认新密码</label>
          <input v-model="passwordForm.confirm" class="input" type="password" autocomplete="new-password" />
        </div>
        <button class="btn" type="button" :disabled="savingPassword" @click="savePassword">
          {{ savingPassword ? '提交中…' : '更新密码' }}
        </button>
        <div class="footnote" style="text-align: left">
          修改成功后会自动退出：服务端用"最小签发时间"让旧访问令牌与刷新令牌同时作废，无需逐个拉黑。
        </div>
      </PanelCard>
    </div>

    <div class="grid2 align-start">
      <PanelCard title="申请叠加身份" more="志愿者 / 认养人">
        <div class="field">
          <label>申请身份</label>
          <select v-model="applyForm.roleCode" class="select">
            <option value="VOLUNTEER">志愿者（可接志愿任务）</option>
            <option value="ADOPTER">认养人（可提交云认养申请）</option>
          </select>
        </div>
        <div v-if="applyForm.roleCode === 'VOLUNTEER'" class="field">
          <label>可参与时段</label>
          <input v-model="applyForm.availableSlots" class="input" placeholder="例如 周三下午 / 周六上午" />
        </div>
        <div class="field">
          <label>申请理由（不少于 10 字）</label>
          <textarea v-model="applyForm.reason" class="textarea" placeholder="例如：希望参与校园流浪猫的绝育与投喂排班。" />
        </div>
        <button class="btn" type="button" :disabled="submittingApply" @click="submitApply">
          {{ submittingApply ? '提交中…' : '提交申请' }}
        </button>
        <div class="footnote" style="text-align: left">
          审核通过后菜单才变化：申请单据会以 PENDING 状态保存，未通过不会提前拿到权限。
        </div>
      </PanelCard>

      <PanelCard title="我的身份申请" :more="`共 ${applications.length} 条`">
        <ul class="list">
          <li v-for="item in applications" :key="item.id">
            <span class="dot" :class="{ gray: item.status !== 'GRANTED' }" />
            <span class="grow">
              {{ item.roleLabel }}
              <span class="muted" style="font-size: 12px">· {{ dateTime(item.appliedAt) }}</span>
              <br />
              <span class="muted" style="font-size: 12px">{{ item.reason ?? '—' }}</span>
              <br />
              <span v-if="item.expireAt" class="muted" style="font-size: 12px">
                临时授权 · {{ dateTime(item.expireAt) }} 到期自动回收
              </span>
            </span>
            <span
              class="tag"
              :class="{ gray: item.status === 'PENDING', danger: item.status === 'REJECTED' }"
            >{{ statusLabel(item.status) }}</span>
          </li>
          <li v-if="!applications.length">
            <span class="dot gray" />
            <span class="grow muted">还没有申请记录</span>
          </li>
        </ul>
      </PanelCard>
    </div>

    <PanelCard title="账号申诉" more="被禁用后同样可在登录页提交">
      <template v-if="appealForm.submitted">
        <div class="notice" style="margin-bottom: 12px">{{ appealForm.message }}</div>
        <button class="btn ghost" type="button" @click="appealForm.submitted = false">再提交一条</button>
      </template>
      <template v-else>
        <div class="field">
          <label>申诉理由（5—500 字）</label>
          <textarea
            v-model="appealForm.reason"
            class="textarea"
            placeholder="例如：账号被误判为违规，附上当时的活动说明。"
          />
        </div>
        <div class="field">
          <label>联系方式（可选）</label>
          <input v-model="appealForm.contact" class="input" placeholder="手机号或邮箱，便于回访" />
        </div>
        <button class="btn" type="button" :disabled="submittingAppeal" @click="submitAppeal">
          {{ submittingAppeal ? '提交中…' : '提交申诉' }}
        </button>
        <div class="footnote" style="text-align: left">
          申诉是公开接口：账号被禁用后无法登录，走登录页的"账号被禁用？申诉"同样能提交。
        </div>
      </template>
    </PanelCard>

    <PanelCard title="我的捐赠" :more="`最近 ${donations.length} 笔 · 仅本人可见`">
      <div v-if="donationsError" class="notice">{{ donationsError }}</div>
      <div v-else-if="donationsLoading" class="muted" style="padding: 8px 0">捐赠记录加载中…</div>
      <div v-else-if="!donations.length" class="muted" style="padding: 8px 0">
        暂无实名捐赠记录。匿名捐赠不会显示在这里。
      </div>
      <table v-else class="native">
        <thead>
          <tr>
            <th>捐赠单号</th>
            <th>项目</th>
            <th>金额</th>
            <th>状态</th>
            <th>登记时间</th>
            <th style="width: 92px">凭证</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in donations" :key="row.donationNo">
            <td class="mono">{{ row.donationNo }}</td>
            <td>{{ row.projectName }}</td>
            <td><b>{{ money(row.amount) }}</b></td>
            <td>
              <div class="donation-status">
                <span
                  class="tag"
                  :class="{ danger: row.status === 'ABNORMAL', gray: row.status === 'PENDING_VERIFY' }"
                >
                  {{ donationStatusLabel(row.status) }}
                </span>
                <span v-if="row.abnormalReason" class="abnormal-reason">{{ row.abnormalReason }}</span>
              </div>
            </td>
            <td class="muted small">{{ dateTime(row.createdAt) }}</td>
            <td>
              <button
                v-if="row.status === 'VERIFIED'"
                class="btn sm link"
                type="button"
                :disabled="certificateBusy === row.donationNo"
                @click="downloadCertificate(row)"
              >
                {{ certificateBusy === row.donationNo ? '生成中…' : '电子凭证' }}
              </button>
              <span v-else class="muted small">入账后可下载</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="footnote" style="text-align: left">
        口径：GET /donations/mine；只有 VERIFIED 显示电子凭证，ABNORMAL 会注明异常原因。
      </div>
    </PanelCard>

    <PanelCard title="登录日志" more="近 30 天，本人可见">
      <table class="native">
        <thead>
          <tr>
            <th>时间</th>
            <th>动作</th>
            <th>结果</th>
            <th>来源 IP</th>
            <th>追踪号</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in loginLogs" :key="log.id">
            <td>{{ dateTime(log.createdAt) }}</td>
            <td>{{ actionLabel(log.action) }}</td>
            <td>
              <span class="tag" :class="{ danger: log.result !== 'SUCCESS', gray: log.result === 'SUCCESS' }">
                {{ log.result === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
            <td class="mono">{{ log.ip ?? '—' }}</td>
            <td class="mono">{{ log.requestId ?? '—' }}</td>
          </tr>
          <tr v-if="!loginLogs.length">
            <td colspan="5" class="muted">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </PanelCard>
  </div>
</template>

<style scoped>
.small {
  font-size: 11px;
}

.donation-status {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.abnormal-reason {
  max-width: 220px;
  color: var(--danger);
  font-size: 11px;
  line-height: 1.5;
  white-space: normal;
}
</style>
