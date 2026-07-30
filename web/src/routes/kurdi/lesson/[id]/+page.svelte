<script lang="ts">
  import { onMount }  from 'svelte';
  import { page }     from '$app/stores';
  import { goto }     from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import {
    fetchLessonContent, completeLesson, XP_PER_TYPE,
    type KfVocab, type KfExercise,
  } from '$lib/services/kurdi.service';

  const lessonId = $derived($page.params.id);

  let lesson    = $state<any>(null);
  let vocab     = $state<KfVocab[]>([]);
  let exercises = $state<KfExercise[]>([]);
  let loading   = $state(true);

  // Aşama: vocab → exercises → sonuç
  let phase     = $state<'vocab' | 'exercise' | 'result'>('vocab');
  let vocabIdx  = $state(0);
  let exIdx     = $state(0);
  let selected  = $state('');
  let checked   = $state(false);
  let isCorrect = $state(false);
  let correctCount = $state(0);
  let fillValue = $state('');
  let xpEarned  = $state(0);

  // Shuffle yardımcıları
  function shuffle<T>(arr: T[]): T[] {
    return [...arr].sort(() => Math.random() - .5);
  }

  // MCQ seçenekleri
  const currentOptions = $derived((() => {
    if (phase !== 'exercise') return [];
    const ex = exercises[exIdx];
    if (!ex || ex.type !== 'mcq') return [];
    const opts = [ex.optA, ex.optB, ex.optC, ex.optD].filter(Boolean);
    return opts.length ? opts : shuffle([ex.answer, ...ex.wrong]).slice(0, 4);
  })());

  // Build — karıştırılmış kelimeler
  let buildPicked = $state<string[]>([]);
  const buildWords = $derived((() => {
    if (phase !== 'exercise') return [];
    const ex = exercises[exIdx];
    if (ex?.type !== 'build') return [];
    return shuffle([...ex.answer.split(' '), ...ex.wrong].filter(Boolean));
  })());

  // Match — state
  let matchSelA = $state<string | null>(null);
  let matchDone = $state<{ a: string; b: string }[]>([]);

  onMount(async () => {
    const res = await fetchLessonContent(lessonId);
    lesson    = res.lesson;
    vocab     = res.vocab;
    exercises = res.exercises;
    loading   = false;
  });

  // ── Vocab aşaması ────────────────────────────────────────────────────────
  function nextVocab() {
    if (vocabIdx < vocab.length - 1) vocabIdx++;
    else phase = 'exercise';
  }
  function prevVocab() { if (vocabIdx > 0) vocabIdx--; }

  // ── Egzersiz kontrolü ────────────────────────────────────────────────────
  function checkAnswer() {
    const ex = exercises[exIdx];
    if (!ex) return;
    checked = true;
    const ans = ex.answer.trim().toLowerCase();

    if (ex.type === 'mcq') {
      isCorrect = selected.trim().toLowerCase() === ans;
    } else if (ex.type === 'fill') {
      isCorrect = fillValue.trim().toLowerCase() === ans;
    } else if (ex.type === 'build') {
      isCorrect = buildPicked.join(' ').toLowerCase() === ans;
    } else if (ex.type === 'match') {
      isCorrect = matchDone.length === (ex.pairs?.length ?? 0);
    }
    if (isCorrect) correctCount++;
  }

  function nextExercise() {
    if (exIdx < exercises.length - 1) {
      exIdx++; checked = false; selected = ''; fillValue = '';
      buildPicked = []; matchSelA = null; matchDone = [];
    } else {
      finishLesson();
    }
  }

  async function finishLesson() {
    phase = 'result';
    if ($currentUser) {
      xpEarned = await completeLesson(
        $currentUser.uid, lessonId, lesson?.nameTr ?? '',
        correctCount, exercises,
      );
    }
  }

  // Match logic
  function pickMatchA(a: string) { matchSelA = a; }
  function pickMatchB(b: string) {
    if (!matchSelA) return;
    const pair = exercises[exIdx]?.pairs?.find(p => p.a === matchSelA);
    if (pair?.b === b) matchDone = [...matchDone, { a: matchSelA, b }];
    matchSelA = null;
    if (matchDone.length === exercises[exIdx]?.pairs?.length) {
      checked = true; isCorrect = true; correctCount++;
    }
  }

  const totalXp = $derived(exercises.reduce((s, ex) => s + (XP_PER_TYPE[ex.type] ?? 2), 0));
  const accuracy = $derived(exercises.length ? Math.round(correctCount / exercises.length * 100) : 0);
</script>

<svelte:head><title>{lesson?.nameTr ?? 'Ders'} — Kurdî</title></svelte:head>

{#if loading}
  <div class="lesson-loading">Yükleniyor…</div>
{:else if !lesson}
  <div class="lesson-loading">Ders bulunamadı.</div>
{:else}

<!-- Topbar -->
<div class="lesson-topbar">
  <button class="back-btn" onclick={() => goto('/kurdi')}>←</button>
  <div class="lesson-topbar-info">
    <span class="lesson-emoji-sm">{lesson.emoji}</span>
    <span class="lesson-title">{lesson.nameTr}</span>
  </div>
  <span class="lesson-phase-chip">{phase === 'vocab' ? `${vocabIdx+1}/${vocab.length} kelime` : phase === 'exercise' ? `${exIdx+1}/${exercises.length} soru` : 'Bitti!'}</span>
</div>

<!-- İlerleme çubuğu -->
<div class="lesson-progress">
  <div class="lesson-progress-fill" style="width:{
    phase === 'vocab' ? (vocabIdx+1)/Math.max(vocab.length,1)*50
    : phase === 'exercise' ? 50 + (exIdx+1)/Math.max(exercises.length,1)*50
    : 100
  }%"></div>
</div>

<div class="lesson-body">

  <!-- VOCAB AŞAMASI -->
  {#if phase === 'vocab'}
    {#if vocab.length === 0}
      <div class="phase-empty">
        <p>Bu derste kelime kartı yok.</p>
        <button class="primary-btn" onclick={() => phase = 'exercise'}>Egzersizlere Geç →</button>
      </div>
    {:else}
      {@const v = vocab[vocabIdx]}
      <div class="vocab-card">
        <span class="vocab-emoji">{v.e || '📖'}</span>
        <p class="vocab-ku">{v.ku}</p>
        {#if v.kp}<p class="vocab-kp">/{v.kp}/</p>{/if}
        <p class="vocab-tr">{v.tr}</p>
      </div>
      <div class="vocab-nav">
        <button class="nav-btn" onclick={prevVocab} disabled={vocabIdx === 0}>←</button>
        <span class="vocab-dots">
          {#each vocab as _, i}
            <span class="dot" class:active={i === vocabIdx}></span>
          {/each}
        </span>
        <button class="nav-btn" onclick={nextVocab}>
          {vocabIdx === vocab.length - 1 ? 'Egzersizlere →' : '→'}
        </button>
      </div>
      {#if lesson.tip}
        <p class="lesson-tip">💡 {lesson.tip}</p>
      {/if}
    {/if}

  <!-- EGZERSİZ AŞAMASI -->
  {:else if phase === 'exercise'}
    {#if exercises.length === 0}
      <div class="phase-empty">
        <p>Bu derste egzersiz yok.</p>
        <button class="primary-btn" onclick={finishLesson}>Dersi Tamamla ✓</button>
      </div>
    {:else}
      {@const ex = exercises[exIdx]}
      <div class="exercise-card">
        <p class="ex-type-label">{ex.type === 'mcq' ? 'Doğru cevabı seç' : ex.type === 'fill' ? 'Boşluğu doldur' : ex.type === 'build' ? 'Cümleyi kur' : 'Eşleştir'}</p>
        <p class="ex-question">{ex.question}</p>
        {#if ex.questionTr}<p class="ex-question-tr">{ex.questionTr}</p>{/if}

        <!-- MCQ -->
        {#if ex.type === 'mcq'}
          <div class="mcq-options">
            {#each currentOptions as opt}
              <button
                class="mcq-btn"
                class:selected={selected === opt}
                class:correct={checked && opt === ex.answer}
                class:wrong={checked && selected === opt && opt !== ex.answer}
                disabled={checked}
                onclick={() => selected = opt}
              >{opt}</button>
            {/each}
          </div>

        <!-- FILL -->
        {:else if ex.type === 'fill'}
          <input
            type="text" class="fill-input" placeholder="Cevabını yaz…"
            bind:value={fillValue} disabled={checked}
          />
          {#if checked && !isCorrect}
            <p class="correct-ans">Doğru: {ex.answer}</p>
          {/if}

        <!-- BUILD -->
        {:else if ex.type === 'build'}
          <div class="build-target">
            {#each buildPicked as w, i}
              <button class="build-word picked" onclick={() => buildPicked = buildPicked.filter((_,j) => j !== i)} disabled={checked}>{w}</button>
            {/each}
            {#if buildPicked.length === 0}<span class="build-placeholder">Kelimelere tıkla…</span>{/if}
          </div>
          <div class="build-words">
            {#each buildWords as w}
              {#if !buildPicked.includes(w)}
                <button class="build-word" onclick={() => buildPicked = [...buildPicked, w]} disabled={checked}>{w}</button>
              {/if}
            {/each}
          </div>
          {#if checked && !isCorrect}
            <p class="correct-ans">Doğru: {ex.answer}</p>
          {/if}

        <!-- MATCH -->
        {:else if ex.type === 'match'}
          <div class="match-grid">
            <div class="match-col">
              {#each ex.pairs ?? [] as pair}
                {#if !matchDone.find(d => d.a === pair.a)}
                  <button class="match-btn" class:sel={matchSelA === pair.a} onclick={() => pickMatchA(pair.a)}>{pair.a}</button>
                {:else}
                  <div class="match-done">{pair.a} ✓</div>
                {/if}
              {/each}
            </div>
            <div class="match-col">
              {#each shuffle(ex.pairs?.map(p => p.b) ?? []) as b}
                {#if !matchDone.find(d => d.b === b)}
                  <button class="match-btn" class:disabled={!matchSelA} onclick={() => pickMatchB(b)}>{b}</button>
                {:else}
                  <div class="match-done">✓</div>
                {/if}
              {/each}
            </div>
          </div>
        {/if}

        <!-- Kontrol / İleri -->
        {#if !checked}
          <button class="primary-btn"
            disabled={ex.type === 'mcq' ? !selected : ex.type === 'fill' ? !fillValue.trim() : ex.type === 'build' ? buildPicked.length === 0 : matchDone.length < (ex.pairs?.length ?? 0)}
            onclick={checkAnswer}
          >Kontrol Et</button>
        {:else}
          <div class="feedback" class:correct={isCorrect} class:wrong={!isCorrect}>
            {isCorrect ? '✅ Doğru!' : '❌ Yanlış'}
          </div>
          <button class="primary-btn" onclick={nextExercise}>
            {exIdx === exercises.length - 1 ? 'Dersi Bitir 🎉' : 'Devam →'}
          </button>
        {/if}
      </div>
    {/if}

  <!-- SONUÇ EKRANI -->
  {:else}
    <div class="result-screen">
      <span class="result-emoji">{accuracy >= 80 ? '🎉' : accuracy >= 50 ? '👍' : '💪'}</span>
      <h2 class="result-title">{accuracy >= 80 ? 'Harika!' : accuracy >= 50 ? 'İyi İş!' : 'Devam Et!'}</h2>
      <p class="result-sub">{lesson.nameTr} tamamlandı</p>
      <div class="result-stats">
        <div class="stat-box">
          <span class="stat-val">⚡ {xpEarned}</span>
          <span class="stat-lbl">XP kazandın</span>
        </div>
        <div class="stat-box">
          <span class="stat-val">🎯 {accuracy}%</span>
          <span class="stat-lbl">Doğruluk</span>
        </div>
        <div class="stat-box">
          <span class="stat-val">✅ {correctCount}/{exercises.length}</span>
          <span class="stat-lbl">Doğru</span>
        </div>
      </div>
      <button class="primary-btn" onclick={() => goto('/kurdi')}>Derslere Dön</button>
    </div>
  {/if}
</div>
{/if}

<style>
.lesson-loading { display:flex;align-items:center;justify-content:center;height:200px;color:var(--muted); }
.lesson-topbar { display:flex;align-items:center;gap:10px;padding:10px 14px;background:var(--surface);border-bottom:1px solid var(--divider);position:sticky;top:0;z-index:10; }
.back-btn { background:none;border:none;cursor:pointer;font-size:1.3rem;color:var(--on-bg);padding:4px; }
.lesson-topbar-info { display:flex;align-items:center;gap:6px;flex:1;min-width:0; }
.lesson-emoji-sm { font-size:1.2rem; }
.lesson-title { font-size:.9rem;font-weight:700;color:var(--on-bg);overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
.lesson-phase-chip { font-size:.72rem;font-weight:700;color:var(--primary);background:color-mix(in srgb,var(--primary) 10%,transparent);border-radius:10px;padding:3px 8px;flex-shrink:0; }
.lesson-progress { height:4px;background:var(--divider); }
.lesson-progress-fill { height:100%;background:var(--primary);transition:width .4s;border-radius:0 2px 2px 0; }
.lesson-body { padding:16px;max-width:600px;margin:0 auto; }

/* Vocab */
.vocab-card { display:flex;flex-direction:column;align-items:center;gap:8px;background:var(--surface);border:1.5px solid var(--divider);border-radius:20px;padding:32px 24px;margin-bottom:20px;text-align:center; }
.vocab-emoji { font-size:3rem; }
.vocab-ku { font-size:1.8rem;font-weight:800;color:var(--on-bg);margin:0; }
.vocab-kp { font-size:1rem;color:var(--muted);margin:0;font-style:italic; }
.vocab-tr { font-size:1.1rem;font-weight:600;color:var(--primary);margin:0; }
.vocab-nav { display:flex;align-items:center;justify-content:center;gap:16px;margin-bottom:12px; }
.nav-btn { background:var(--surface-var);border:1.5px solid var(--divider);border-radius:50%;width:40px;height:40px;cursor:pointer;font-size:1.1rem;display:flex;align-items:center;justify-content:center; }
.nav-btn:disabled { opacity:.3;cursor:default; }
.vocab-dots { display:flex;gap:5px; }
.dot { width:7px;height:7px;border-radius:50%;background:var(--divider); }
.dot.active { background:var(--primary); }
.lesson-tip { font-size:.8rem;color:var(--muted);text-align:center;background:color-mix(in srgb,#F59E0B 8%,transparent);border-radius:10px;padding:10px;margin:0; }

/* Exercise */
.exercise-card { display:flex;flex-direction:column;gap:14px; }
.ex-type-label { font-size:.72rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--muted);margin:0; }
.ex-question { font-size:1.05rem;font-weight:700;color:var(--on-bg);margin:0;line-height:1.4; }
.ex-question-tr { font-size:.85rem;color:var(--muted);margin:0; }
.mcq-options { display:flex;flex-direction:column;gap:8px; }
.mcq-btn { padding:13px;border-radius:14px;border:1.5px solid var(--divider);background:var(--surface);font-size:.9rem;cursor:pointer;font-family:inherit;color:var(--on-bg);transition:all .15s;text-align:left; }
.mcq-btn.selected { border-color:var(--primary);background:color-mix(in srgb,var(--primary) 8%,transparent); }
.mcq-btn.correct  { border-color:#22c55e;background:color-mix(in srgb,#22c55e 10%,transparent);color:#166534; }
.mcq-btn.wrong    { border-color:#ef4444;background:color-mix(in srgb,#ef4444 10%,transparent);color:#991b1b; }
.fill-input { border:1.5px solid var(--divider);border-radius:12px;padding:12px 14px;font-size:1rem;font-family:inherit;color:var(--on-bg);background:var(--surface-var);outline:none;width:100%;box-sizing:border-box; }
.correct-ans { margin:0;font-size:.82rem;color:#22c55e;font-weight:600; }
.build-target { min-height:48px;border:1.5px dashed var(--divider);border-radius:12px;padding:10px;display:flex;flex-wrap:wrap;gap:6px;background:var(--surface-var); }
.build-placeholder { color:var(--muted);font-size:.85rem;align-self:center; }
.build-words { display:flex;flex-wrap:wrap;gap:6px; }
.build-word { padding:8px 14px;border-radius:20px;border:1.5px solid var(--primary);background:color-mix(in srgb,var(--primary) 8%,transparent);color:var(--primary);font-size:.88rem;font-weight:600;cursor:pointer;font-family:inherit; }
.build-word.picked { background:var(--primary);color:#fff; }
.match-grid { display:grid;grid-template-columns:1fr 1fr;gap:8px; }
.match-col { display:flex;flex-direction:column;gap:6px; }
.match-btn { padding:10px;border:1.5px solid var(--divider);border-radius:12px;background:var(--surface);font-size:.88rem;cursor:pointer;font-family:inherit;color:var(--on-bg);transition:all .15s;text-align:center; }
.match-btn.sel { border-color:var(--primary);background:color-mix(in srgb,var(--primary) 10%,transparent); }
.match-btn.disabled { opacity:.5;cursor:default; }
.match-done { padding:10px;border-radius:12px;background:color-mix(in srgb,#22c55e 10%,transparent);border:1.5px solid #22c55e;font-size:.88rem;text-align:center;color:#166534; }
.feedback { padding:12px;border-radius:12px;font-weight:700;font-size:.95rem;text-align:center; }
.feedback.correct { background:color-mix(in srgb,#22c55e 10%,transparent);color:#166534; }
.feedback.wrong   { background:color-mix(in srgb,#ef4444 10%,transparent);color:#991b1b; }
.primary-btn { padding:14px;border-radius:14px;border:none;background:var(--primary);color:#fff;font-weight:700;font-size:.95rem;cursor:pointer;font-family:inherit;width:100%;transition:opacity .15s; }
.primary-btn:disabled { opacity:.4;cursor:default; }
.phase-empty { display:flex;flex-direction:column;align-items:center;gap:14px;padding:40px 0;text-align:center;color:var(--muted); }

/* Result */
.result-screen { display:flex;flex-direction:column;align-items:center;gap:14px;padding:40px 0;text-align:center; }
.result-emoji { font-size:4rem; }
.result-title { font-size:1.6rem;font-weight:800;margin:0; }
.result-sub { font-size:.9rem;color:var(--muted);margin:0; }
.result-stats { display:flex;gap:10px;margin:8px 0; }
.stat-box { display:flex;flex-direction:column;gap:4px;align-items:center;background:var(--surface);border:1.5px solid var(--divider);border-radius:14px;padding:14px 18px; }
.stat-val { font-size:1.1rem;font-weight:800; }
.stat-lbl { font-size:.72rem;color:var(--muted); }
</style>
