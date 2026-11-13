<script setup lang="ts">
defineProps<{ note: string }>()
</script>

<template>
  <div v-if="note" class="booking-note">
    <strong>预约须知</strong>
    <p class="preserve-lines">{{ note }}</p>
  </div>
</template>
