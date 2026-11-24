<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="auth" style="min-height: 100vh">
    <div class="auth-card" style="text-align: center">
      <h1 style="font-size: 40px; color: var(--accent)">404</h1>
      <button class="btn" type="button" @click="router.replace('/')">回工作台</button>
    </div>
  </div>
</template>
