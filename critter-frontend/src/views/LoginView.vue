<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as authApi from '@/api/auth'
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

type Mode = 'login' | 'register' | 'verify'

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
    <aside class="auth-side">
      <div>
        <div class="brand">
          <div class="brand-mark">青</div>
          <div>
            <b>青檐万灵册</b>
            <small>校园生物档案与救助平台</small>
          </div>
        </div>
        <h1>让每一只校园生灵<br />都被看见、被记录</h1>
        <p>
          从一次上报开始，到建档、治疗、认养、募捐与结项公示，
          每一步都留痕、每一笔都说得清。
        </p>
        <ul class="auth-points">
          <li>实名学籍校验，只对本校师生开放</li>
          <li>状态机驱动的档案流转，非法操作拦得住</li>
          <li>资金双人复核，台账与档案可互相追问</li>
        </ul>
      </div>
      <div class="muted" style="font-size: 12px">软件体系结构课程设计 · 三人小组 · 2026 秋</div>
    </aside>

    <main class="auth-main">
      <div class="auth-card">
        <div class="auth-tabs">
          <button type="button" :class="{ on: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" :class="{ on: mode !== 'login' }" @click="switchMode('register')">注册</button>
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
          <div class="auth-foot">连续 5 次密码错误将锁定 10 分钟</div>
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
            <label>昵称</label>
            <input v-model="registerForm.nickname" class="input" :class="{ invalid: !!fieldErrors.nickname }" placeholder="2—16 字，公开页展示" />
            <div v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</div>
          </div>
          <div class="field">
            <label>院系（可选，默认取学籍名单）</label>
            <input v-model="registerForm.dept" class="input" placeholder="例如 计算机学院" />
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
          <div class="auth-foot">学号需在校内学籍名单内，注册后需邮箱验证码激活</div>
        </form>

        <!-- 验证码 -->
        <form v-else @submit.prevent="submitVerify">
          <div class="notice" style="margin-bottom: 16px">
            验证码已发送至 <b>{{ verifyState.email }}</b>，{{ verifyState.countdown }} 秒内有效。
          </div>
          <div v-if="verifyState.devCode" class="demo-accounts">
            演示环境未接入邮件通道，本次验证码为 <b>{{ verifyState.devCode }}</b>
            （生产环境置 <code>critter.register.expose-verification-code=false</code> 即不再回传）
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

        <div class="demo-accounts">
          演示账号（本地库已播种）<br />
          <b>学生</b> 2023019001 / Qy@2026 ·
          <b>动物管理员</b> 2023010101 / Admin@2026 ·
          <b>系统管理员</b> 2023010102 / Admin@2026
          <div style="margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap">
            <button class="btn sm ghost" type="button" @click="useDemo('2023019001', 'Qy@2026')">填入学生</button>
            <button class="btn sm ghost" type="button" @click="useDemo('2023010101', 'Admin@2026')">填入动物管理员</button>
            <button class="btn sm ghost" type="button" @click="useDemo('2023010102', 'Admin@2026')">填入系统管理员</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>
