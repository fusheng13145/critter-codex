<script setup lang="ts">
/**
 * 根组件只留路由出口：布局在 layouts/AppLayout.vue，
 * 登录页与 404 不套外壳，所以不能把布局写死在根上。
 */
</script>

<template>
  <router-view />
</template>
