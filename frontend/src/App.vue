<template>
  <div class="app-shell">
    <aside class="sidebar">
      <h1>课题二监控台</h1>
      <nav>
        <RouterLink to="/overview">总览</RouterLink>
        <RouterLink to="/nodes">节点</RouterLink>
        <RouterLink to="/services">服务</RouterLink>
      </nav>
      <div class="hint">
        <p>容器化开发/运行/测试</p>
        <p>专门扫描模拟 Linux 节点</p>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
</script>
