<script lang="ts">
  import { onMount } from 'svelte';
  import { page }   from '$app/stores';
  import { currentUser } from '$lib/stores/auth';
  import {
    fetchUnitsAndLessons, fetchGrammarRules,
    type KfUnit, type KfLesson, type GrammarRule,
  } from '$lib/services/kurdi.service';
  import TabBar    from '$lib/components/TabBar.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Skeleton  from '$lib/components/Skeleton.svelte';
  import PullToRefresh from '$lib/components/PullToRefresh.svelte';

  const tabs = ['Üniteler', 'Dilbilgisi'];
  let activeTab  = $state(0);
  let units      = $state<KfUnit[]>([]);
  let lessons    = $state<KfLesson[]>([]);
  let grammar    = $state<GrammarRule[]>([]);
  let loading    = $state(true);
  let grammarLoaded = $state(false);

  // Deeplink: ?openLesson=id veya tab=grammar
  onMount(async () => {
    const tab = $page.url.searchParams.get('tab');
    if (tab === 'grammar') activeTab = 1;
    await load();
    const openLesson = $page.url.searchParams.get('openLesson');
    if (openLesson) goto(`/kurdi/lesson/${openLesson}`);
  });

  async function load() {
    loading = true;
    const res = await fetchUnitsAndLessons($currentUser?.uid ?? undefined);
    units   = res.units;
    lessons = res.lessons;
    loading = false;
  }

  async function loadGrammar() {
    if (grammarLoaded) return;
    grammar = await fetchGrammarRules();
    grammarLoaded = true;
  }

  $effect(() => {
    if (activeTab === 1 && !grammarLoaded) loadGrammar();
  });

  async function handleRefresh() { await load(); grammarLoaded = false; }

  // Ünite altındaki dersler
  function lessonsFor(unitId: string): KfLesson[] {
    return lessons.filter(l => l.unitId === unitId);
  }

  // Tamamlanma yüzdesi
  function progress(unitId: string): number {
    const ls = lessonsFor(unitId);
    if (!ls.length) return 0;
    return Math.round(ls.filter(l => l.completed).length / ls.length * 100);
  }

  import { goto } from '$app/navigation';
</script>

<svelte:head>
  <title>Kurdî Fêrbûn — Heft Reng</title>
  <meta name="description" content="Kurmancî öğren! Interaktif dersler, gramer kuralları ve günlük pratikle Kürtçeyi adım adım öğren." />
  <meta property="og:title"       content="Kurdî Fêrbûn — Heft Reng" />
  <meta property="og:description" content="Kurmancî öğren! Interaktif dersler, gramer kuralları ve günlük pratikle Kürtçeyi adım adım öğren." />
  <meta property="og:url"         content="https://heftreng.onrender.com/kurdi" />
  <meta property="og:image"       content="https://heftreng.onrender.com/og-default.png" />
  <meta name="twitter:title"      content="Kurdî Fêrbûn — Heft Reng" />
  <meta name="twitter:description" content="Kurmancî öğren! Interaktif dersler, gramer kuralları ve günlük pratikle Kürtçeyi adım adım öğren." />
</svelte:head>

<PullToRefresh onRefresh={handleRefresh}>
  <div class="kurdi-page">
    <div class="kurdi-header">
      <h2>🇹🇷 Kurdî Öğren</h2>
      {#if $currentUser}
        <span class="xp-chip">⚡ {$currentUser.displayName}</span>
      {/if}
    </div>

    <TabBar {tabs} bind:active={activeTab} stickyTop={52} />

    {#if loading}
      <div class="sk-list">
        {#each {length: 4} as _}
          <div class="sk-unit">
            <Skeleton width="48px" height="48px" radius="14px" />
            <div style="flex:1">
              <Skeleton width="50%" height="15px" />
              <Skeleton width="70%" height="11px" />
              <Skeleton width="100%" height="6px" radius="3px" />
            </div>
          </div>
        {/each}
      </div>

    {:else if activeTab === 0}
      <!-- Üniteler -->
      {#if units.length === 0}
        <EmptyState icon="📚" message="Henüz ders eklenmemiş." />
      {:else}
        <div class="units-list">
          {#each units as unit (unit.id)}
            {@const unitLessons = lessonsFor(unit.id)}
            {@const pct = progress(unit.id)}
            <div class="unit-block">
              <!-- Ünite başlık -->
              <div class="unit-header" style="border-left: 4px solid {unit.color}">
                <span class="unit-icon">{unit.icon}</span>
                <div class="unit-info">
                  <p class="unit-name">{unit.ttl}</p>
                  {#if unit.nameKu}<p class="unit-nameku">{unit.nameKu}</p>{/if}
                  <!-- İlerleme çubuğu -->
                  <div class="progress-bar">
                    <div class="progress-fill" style="width:{pct}%; background:{unit.color}"></div>
                  </div>
                  <p class="unit-progress">{unitLessons.filter(l=>l.completed).length}/{unitLessons.length} ders · {pct}%</p>
                </div>
              </div>

              <!-- Dersler grid -->
              <div class="lessons-grid">
                {#each unitLessons as lesson (lesson.id)}
                  <a
                    href="/kurdi/lesson/{lesson.id}"
                    class="lesson-card"
                    class:completed={lesson.completed}
                    style="--unit-color:{unit.color}"
                  >
                    <span class="lesson-emoji">{lesson.emoji}</span>
                    <span class="lesson-name">{lesson.nameTr}</span>
                    {#if lesson.nameKu}<span class="lesson-nameku">{lesson.nameKu}</span>{/if}
                    <div class="lesson-footer">
                      <span class="lesson-xp">⚡{lesson.xp} XP</span>
                      {#if lesson.completed}
                        <span class="lesson-done">✓</span>
                      {/if}
                    </div>
                  </a>
                {/each}
              </div>
            </div>
          {/each}
        </div>
      {/if}

    {:else}
      <!-- Dilbilgisi -->
      {#if !grammarLoaded}
        <div class="sk-list">
          {#each {length: 3} as _}
            <div class="sk-unit">
              <Skeleton width="100%" height="80px" radius="14px" />
            </div>
          {/each}
        </div>
      {:else if grammar.length === 0}
        <EmptyState icon="📖" message="Henüz dilbilgisi kuralı eklenmemiş." />
      {:else}
        <div class="grammar-list">
          {#each grammar as rule (rule.id)}
            <a href="/kurdi/grammar/{rule.id}" class="grammar-card">
              <p class="grammar-title">{rule.titleTr || rule.title}</p>
              {#if rule.title && rule.titleTr}
                <p class="grammar-title-ku">{rule.title}</p>
              {/if}
              <p class="grammar-preview">{rule.contentTr.slice(0, 100)}…</p>
            </a>
          {/each}
        </div>
      {/if}
    {/if}
  </div>
</PullToRefresh>

<style>
.kurdi-page { min-height: 100dvh; padding-bottom: 80px; }
.kurdi-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 8px; }
.kurdi-header h2 { margin: 0; font-size: 1.1rem; font-weight: 800; }
.xp-chip { font-size: .75rem; font-weight: 700; color: var(--primary);
  background: color-mix(in srgb,var(--primary) 10%,transparent);
  border-radius: 12px; padding: 3px 10px; }

.units-list { display: flex; flex-direction: column; gap: 0; }
.unit-block { border-bottom: 6px solid var(--divider); padding-bottom: 8px; }
.unit-header { display: flex; align-items: flex-start; gap: 12px; padding: 14px 16px 8px; }
.unit-icon { font-size: 2rem; flex-shrink: 0; margin-top: 2px; }
.unit-info { flex: 1; min-width: 0; }
.unit-name { margin: 0 0 2px; font-size: 1rem; font-weight: 700; color: var(--on-bg); }
.unit-nameku { margin: 0 0 8px; font-size: .78rem; color: var(--muted); }
.progress-bar { height: 6px; background: var(--divider); border-radius: 3px; overflow: hidden; margin-bottom: 4px; }
.progress-fill { height: 100%; border-radius: 3px; transition: width .4s; }
.unit-progress { margin: 0; font-size: .72rem; color: var(--muted); }

.lessons-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px,1fr)); gap: 8px; padding: 4px 16px 10px; }
.lesson-card {
  display: flex; flex-direction: column; gap: 4px;
  background: var(--surface); border: 1.5px solid var(--divider);
  border-radius: 14px; padding: 12px; text-decoration: none; color: inherit;
  transition: box-shadow .15s, transform .15s, border-color .15s;
}
.lesson-card:hover { box-shadow: 0 3px 12px rgba(0,0,0,.08); transform: translateY(-1px); border-color: var(--unit-color); }
.lesson-card.completed { background: color-mix(in srgb, var(--unit-color) 8%, var(--surface)); border-color: var(--unit-color); }
.lesson-emoji { font-size: 1.6rem; }
.lesson-name { font-size: .82rem; font-weight: 700; color: var(--on-bg); line-height: 1.3; }
.lesson-nameku { font-size: .72rem; color: var(--muted); }
.lesson-footer { display: flex; align-items: center; justify-content: space-between; margin-top: auto; }
.lesson-xp { font-size: .68rem; font-weight: 700; color: var(--muted); }
.lesson-done { font-size: .8rem; color: #22c55e; font-weight: 700; }

.grammar-list { display: flex; flex-direction: column; gap: 10px; padding: 12px 14px; }
.grammar-card {
  background: var(--surface); border: 1px solid var(--divider);
  border-radius: 14px; padding: 14px; text-decoration: none; color: inherit;
  transition: box-shadow .15s;
}
.grammar-card:hover { box-shadow: 0 3px 12px rgba(0,0,0,.08); }
.grammar-title { margin: 0 0 2px; font-size: .95rem; font-weight: 700; color: var(--on-bg); }
.grammar-title-ku { margin: 0 0 6px; font-size: .78rem; color: var(--primary); }
.grammar-preview { margin: 0; font-size: .78rem; color: var(--muted); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

.sk-list { display: flex; flex-direction: column; gap: 12px; padding: 12px 14px; }
.sk-unit { display: flex; gap: 12px; align-items: flex-start; }
</style>
