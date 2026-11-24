<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

/**
 * P00 登录 / 注册（含学籍校验与邮箱验证码激活）。
 * 规则与服务端一致：学号 10 位数字、校内邮箱、密码 8—32 位且字母+数字。
 * 服务端返回 30301 时按 field 标红到具体输入框，其余错误显示在表单顶部提示条。
 */

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

type Mode = 'login' | 'register' | 'verify' | 'forgot' | 'appeal'

const mode = ref<Mode>('login')
const submitting = ref(false)
const formError = ref('')
const fieldErrors = reactive<Record<string, string>>({})

const loginForm = reactive({ account: '', password: '' })

const registerForm = reactive({
  account: '',
  email: '',
  nickname: '',
  dept: '',
  password: '',
  confirm: '',
})

const verifyState = reactive({ email: '', code: '', devCode: '', countdown: 0 })
let timer: number | undefined

/** 找回密码三步：发码 → 验证码 + 新密码 → 回登录。 */
const forgotState = reactive({
  account: '',
  code: '',
  password: '',
  confirm: '',
  devCode: '',
  emailMasked: '',
  countdown: 0,
  sent: false,
})
let forgotTimer: number | undefined

/** 账号禁用申诉：被禁用的账号登不进来，只能从登录页提交。 */
const appealState = reactive({ userNo: '', reason: '', contact: '', message: '', done: false })

function resetErrors(): void {
  formError.value = ''
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function applyError(error: unknown): void {
  if (error instanceof BizError) {
    formError.value = error.message
    error.fieldErrors.forEach((item) => {
      fieldErrors[item.field] = item.reason
    })
    return
  }
  formError.value = '请求失败，请确认后端已启动'
}

async function submitLogin(): Promise<void> {
  resetErrors()
  if (!loginForm.account.trim() || !loginForm.password) {
    formError.value = '请输入学号与密码'
    return
  }
  submitting.value = true
  try {
    await auth.login(loginForm.account.trim(), loginForm.password)
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (error) {
    applyError(error)
  } finally {
    submitting.value = false
  }
}

async function submitRegister(): Promise<void> {
  resetErrors()
  if (registerForm.password !== registerForm.confirm) {
    fieldErrors.confirm = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    const result = await authApi.register({
      account: registerForm.account.trim(),
      email: registerForm.email.trim(),
      nickname: registerForm.nickname.trim(),
      dept: registerForm.dept.trim() || undefined,
      password: registerForm.password,
    })
    verifyState.email = result.email
    verifyState.devCode = result.devCode ?? ''
    mode.value = 'verify'
    startCountdown(result.codeTtlSeconds ?? 300)
    ElMessage.success('注册已提交，请完成邮箱验证码校验')
  } catch (error) {
    applyError(error)
  } finally {
    submitting.value = false
  }
}

async function submitVerify(): Promise<void> {
  resetErrors()
  if (!verifyState.code.trim()) {
    formError.value = '请输入验证码'
    return
  }
  submitting.value = true
  try {
    await authApi.verifyRegister(verifyState.email, verifyState.code.trim())
    ElMessage.success('邮箱已验证，请使用新账号登录')
    mode.value = 'login'
    loginForm.account = registerForm.account.trim()
    loginForm.password = ''
    verifyState.code = ''
  } catch (error) {
    applyError(error)
  } finally {
    submitting.value = false
  }
}

function startCountdown(seconds: number): void {
  window.clearInterval(timer)
  verifyState.countdown = Math.min(seconds, 300)
  timer = window.setInterval(() => {
    verifyState.countdown -= 1
    if (verifyState.countdown <= 0) {
      window.clearInterval(timer)
    }
  }, 1000)
}

// ---------------- 找回密码 ----------------

async function sendResetCode(): Promise<void> {
  resetErrors()
  if (!forgotState.account.trim()) {
    fieldErrors.account = '请输入学号或校园邮箱'
    return
  }
  submitting.value = true
  try {
    const result = await authApi.requestPasswordCode(forgotState.account.trim())
    forgotState.sent = true
    forgotState.devCode = result.devCode ?? ''
    forgotState.emailMasked = result.emailMasked
    window.clearInterval(forgotTimer)
    forgotState.countdown = Math.min(result.codeTtlSeconds ?? 300, 300)
    forgotTimer = window.setInterval(() => {
      forgotState.countdown -= 1
      if (forgotState.countdown <= 0) window.clearInterval(forgotTimer)
    }, 1000)
    ElMessage.success(`验证码已发送至 ${result.emailMasked}`)
  } catch (error) {
    applyError(error)
  } finally {
    submitting.value = false
  }
}

async function submitResetPassword(): Promise<void> {
  resetErrors()
  if (forgotState.password !== forgotState.confirm) {
    fieldErrors.confirm = '两次输入的新密码不一致'
    return
  }
  submitting.value = true
  try {
    await authApi.resetPassword(forgotState.account.trim(), forgotState.code.trim(), forgotState.password)
    ElMessage.success('密码已重置，请用新密码登录')
    loginForm.account = forgotState.account.trim()
    loginForm.password = ''
    Object.assign(forgotState, {
      code: '',
      password: '',
      confirm: '',
      devCode: '',
      sent: false,
      countdown: 0,
    })
    window.clearInterval(forgotTimer)
    mode.value = 'login'
  } catch (error) {
    applyError(error)
  } finally {
    submitting.value = false
  }
}

// ---------------- 账号申诉 ----------------

async function submitAppeal(): Promise<void> {
  resetErrors()
  if (!appealState.userNo.trim() || appealState.reason.trim().length < 5) {
    formError.value = '请填写学号与至少 5 个字的申诉理由'
    return
  }
  submitting.value = true
  try {
    const result = await userApi.submitAppeal({
      userNo: appealState.userNo.trim(),
      reason: appealState.reason.trim(),
      contact: appealState.contact.trim() || undefined,
    })
    appealState.done = true
    appealState.message = result.message
  } catch (error) {
    applyError(error)
  } finally {
    submitting.value = false
  }
}

function switchMode(next: Mode): void {
  resetErrors()
  mode.value = next
}

function useDemo(account: string, password: string): void {
  resetErrors()
  mode.value = 'login'
  loginForm.account = account
  loginForm.password = password
}
</script>

<template>
  <div class="auth">
    <div class="auth-brand">
      <div class="brand-mark">青</div>
      <b>青檐万灵册</b>
    </div>

    <div class="auth-card">
        <div class="auth-tabs">
          <button type="button" :class="{ on: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" :class="{ on: mode === 'register' || mode === 'verify' }" @click="switchMode('register')">注册</button>
        </div>

        <div v-if="formError" class="notice" style="border-left-color: var(--danger); color: var(--danger); margin-bottom: 14px">
          {{ formError }}
        </div>

        <!-- 登录 -->
        <form v-if="mode === 'login'" @submit.prevent="submitLogin">
          <div class="field">
            <label>学号 / 校园邮箱</label>
            <input
              v-model="loginForm.account"
              class="input"
              :class="{ invalid: !!fieldErrors.account }"
              placeholder="例如 2023019001"
              autocomplete="username"
            />
            <div v-if="fieldErrors.account" class="field-error">{{ fieldErrors.account }}</div>
          </div>
          <div class="field">
            <label>密码</label>
            <input
              v-model="loginForm.password"
              class="input"
              :class="{ invalid: !!fieldErrors.password }"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
            <div v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</div>
          </div>
          <button class="btn" style="width: 100%" type="submit" :disabled="submitting">
            {{ submitting ? '登录中…' : '登录' }}
          </button>
          <div style="display: flex; gap: 8px; margin-top: 10px">
            <button class="btn ghost sm" type="button" style="flex: 1" @click="switchMode('forgot')">忘记密码？</button>
            <button class="btn ghost sm" type="button" style="flex: 1" @click="switchMode('appeal')">账号被禁用？申诉</button>
          </div>
        </form>

        <!-- 注册 -->
        <form v-else-if="mode === 'register'" @submit.prevent="submitRegister">
          <div class="field">
            <label>学号（10 位数字）</label>
            <input v-model="registerForm.account" class="input" :class="{ invalid: !!fieldErrors.account }" placeholder="例如 2023019001" />
            <div v-if="fieldErrors.account" class="field-error">{{ fieldErrors.account }}</div>
          </div>
          <div class="field">
            <label>校园邮箱</label>
            <input v-model="registerForm.email" class="input" :class="{ invalid: !!fieldErrors.email }" placeholder="例如 2023019001@stu.just.edu.cn" />
            <div v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</div>
          </div>
          <div class="field">
            <label>昵称（2—16 字）</label>
            <input v-model="registerForm.nickname" class="input" :class="{ invalid: !!fieldErrors.nickname }" />
            <div v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</div>
          </div>
          <div class="field">
            <label>院系（可选）</label>
            <input v-model="registerForm.dept" class="input" placeholder="计算机学院" />
          </div>
          <div class="field">
            <label>密码（8—32 位，含字母与数字）</label>
            <input v-model="registerForm.password" class="input" :class="{ invalid: !!fieldErrors.password }" type="password" />
            <div v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</div>
          </div>
          <div class="field">
            <label>确认密码</label>
            <input v-model="registerForm.confirm" class="input" :class="{ invalid: !!fieldErrors.confirm }" type="password" />
            <div v-if="fieldErrors.confirm" class="field-error">{{ fieldErrors.confirm }}</div>
          </div>
          <button class="btn" style="width: 100%" type="submit" :disabled="submitting">
            {{ submitting ? '提交中…' : '注册并发送验证码' }}
          </button>
        </form>

        <!-- 验证码 -->
        <form v-else-if="mode === 'verify'" @submit.prevent="submitVerify">
          <div class="state-line">
            已发送至 <b>{{ verifyState.email }}</b> · 剩余 {{ verifyState.countdown }}s
          </div>
          <div v-if="verifyState.devCode" class="demo-accounts">
            验证码 <b>{{ verifyState.devCode }}</b>
          </div>
          <div class="field" style="margin-top: 16px">
            <label>6 位验证码</label>
            <input v-model="verifyState.code" class="input" maxlength="6" placeholder="请输入验证码" />
          </div>
          <button class="btn" style="width: 100%" type="submit" :disabled="submitting">
            {{ submitting ? '校验中…' : '激活账号' }}
          </button>
          <button class="btn ghost" style="width: 100%; margin-top: 8px" type="button" @click="switchMode('register')">
            返回修改注册信息
          </button>
        </form>

        <!-- 找回密码 -->
        <form v-else-if="mode === 'forgot'" @submit.prevent="forgotState.sent ? submitResetPassword() : sendResetCode()">
          <div class="field">
            <label>学号 / 校园邮箱</label>
            <input
              v-model="forgotState.account"
              class="input"
              :class="{ invalid: !!fieldErrors.account }"
              :disabled="forgotState.sent"
              placeholder="例如 2023019001"
            />
            <div v-if="fieldErrors.account" class="field-error">{{ fieldErrors.account }}</div>
          </div>

          <template v-if="forgotState.sent">
            <div class="demo-accounts" style="margin-bottom: 12px">
              已发送至 <b>{{ forgotState.emailMasked }}</b> · 剩余 {{ forgotState.countdown }}s
              <template v-if="forgotState.devCode">
                <br />验证码 <b>{{ forgotState.devCode }}</b>
              </template>
            </div>
            <div class="field">
              <label>6 位验证码</label>
              <input v-model="forgotState.code" class="input" maxlength="6" placeholder="请输入验证码" />
            </div>
            <div class="field">
              <label>新密码（8—32 位，含字母与数字）</label>
              <input
                v-model="forgotState.password"
                class="input"
                :class="{ invalid: !!fieldErrors.newPassword }"
                type="password"
              />
              <div v-if="fieldErrors.newPassword" class="field-error">{{ fieldErrors.newPassword }}</div>
            </div>
            <div class="field">
              <label>确认新密码</label>
              <input
                v-model="forgotState.confirm"
                class="input"
                :class="{ invalid: !!fieldErrors.confirm }"
                type="password"
              />
              <div v-if="fieldErrors.confirm" class="field-error">{{ fieldErrors.confirm }}</div>
            </div>
            <button class="btn" style="width: 100%" type="submit" :disabled="submitting">
              {{ submitting ? '重置中…' : '重置密码' }}
            </button>
          </template>
          <button v-else class="btn" style="width: 100%" type="submit" :disabled="submitting">
            {{ submitting ? '发送中…' : '发送验证码' }}
          </button>

          <button class="btn ghost" style="width: 100%; margin-top: 8px" type="button" @click="switchMode('login')">
            返回登录
          </button>
        </form>

        <!-- 账号禁用申诉 -->
        <form v-else @submit.prevent="submitAppeal">
          <template v-if="appealState.done">
            <div class="notice" style="margin-bottom: 16px">{{ appealState.message }}</div>
            <button class="btn" style="width: 100%" type="button" @click="switchMode('login')">返回登录</button>
          </template>
          <template v-else>
            <div class="field">
              <label>学号 / 工号</label>
              <input v-model="appealState.userNo" class="input" placeholder="例如 2023010105" />
            </div>
            <div class="field">
              <label>申诉理由（5—500 字）</label>
              <textarea
                v-model="appealState.reason"
                class="input"
                rows="4"
                style="resize: vertical"
              />
            </div>
            <div class="field">
              <label>联系方式（可选）</label>
              <input v-model="appealState.contact" class="input" placeholder="手机号或邮箱" />
            </div>
            <button class="btn" style="width: 100%" type="submit" :disabled="submitting">
              {{ submitting ? '提交中…' : '提交申诉' }}
            </button>
            <button class="btn ghost" style="width: 100%; margin-top: 8px" type="button" @click="switchMode('login')">
              返回登录
            </button>
          </template>
        </form>

        <div class="demo-accounts">
          <button class="btn sm ghost" type="button" @click="useDemo('2023019001', 'Qy@2026')">学生 2023019001</button>
          <button class="btn sm ghost" type="button" @click="useDemo('2023010101', 'Admin@2026')">动物管理员 2023010101</button>
          <button class="btn sm ghost" type="button" @click="useDemo('2023010102', 'Admin@2026')">系统管理员 2023010102</button>
        </div>
      </div>
  </div>
</template>
