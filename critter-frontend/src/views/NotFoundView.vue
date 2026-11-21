<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="auth-main" style="min-height: 100vh">
    <div class="auth-card" style="text-align: center">
      <h1 style="font-size: 48px; color: var(--accent)">404</h1>
      <p class="muted">这个页面不在册上。可能是链接过期，或者菜单被权限收回了。</p>
      <button class="btn" type="button" @click="router.replace('/')">回工作台</button>
    </div>
  </div>
</template>
