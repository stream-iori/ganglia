<script setup lang="ts">
import { onMounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import MainStream from './components/MainStream.vue'
import Inspector from './components/Inspector.vue'
import { eventBusService } from './services/eventbus'

onMounted(() => {
  eventBusService.connect()
})
</script>

<template>
  <div class="flex h-screen w-screen overflow-hidden bg-slate-950 font-sans antialiased text-slate-200">
    <Sidebar />
    <MainStream />
    <Inspector />
  </div>
</template>

<style>
/* Global scrollbar styling */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #1e293b; /* slate-800 */
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: #334155; /* slate-700 */
}
</style>
