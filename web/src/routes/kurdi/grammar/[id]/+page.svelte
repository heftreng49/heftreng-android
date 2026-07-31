<script lang="ts">
  import { onMount }        from 'svelte';
  import { page }           from '$app/stores';
  import { goto }           from '$app/navigation';
  import { fetchGrammarRules, type GrammarRule } from '$lib/services/kurdi.service';

  const id    = $derived($page.params.id);
  let rule    = $state<GrammarRule | null>(null);
  let loading = $state(true);

  onMount(async () => {
    const rules = await fetchGrammarRules();
    rule    = rules.find(r => r.id === id) ?? null;
    loading = false;
  });

  /**
   * Basit Markdown → HTML parser
   * Android Markwon'un desteklediği formatlara uygun:
   * ## Başlık, **bold**, *italic*, `kod`, - liste, | tablo |, \n paragraf
   */
  function markdownToHtml(md: string): string {
    if (!md) return '';

    // Satırları işle
    const lines = md.split('\n');
    const out: string[] = [];
    let inUl      = false;
    let inTable   = false;
    let tableHead = true;
    let i = 0;

    const inlineFormat = (s: string) => s
      .replace(/\*\*(.+?)\*\*/g,  '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g,      '<em>$1</em>')
      .replace(/_(.+?)_/g,        '<em>$1</em>')
      .replace(/`(.+?)`/g,        '<code>$1</code>')
      .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" target="_blank">$1</a>');

    const closeUl = () => { if (inUl) { out.push('</ul>'); inUl = false; } };
    const closeTable = () => {
      if (inTable) { out.push('</tbody></table>'); inTable = false; tableHead = true; }
    };

    while (i < lines.length) {
      const line = lines[i];
      const trimmed = line.trim();

      // Tablo satırı (| ile başlıyorsa)
      if (trimmed.startsWith('|')) {
        if (!inTable) {
          closeUl();
          out.push('<table class="md-table"><thead><tr>');
          const headers = trimmed.split('|').filter(c => c.trim() !== '');
          headers.forEach(h => out.push(`<th>${inlineFormat(h.trim())}</th>`));
          out.push('</tr></thead><tbody>');
          inTable = true; tableHead = false;
          // Ayraç satırını atla
          if (i + 1 < lines.length && lines[i+1].trim().match(/^\|[\s\-\|:]+\|$/)) i++;
        } else {
          // Tablo body satırı
          const cells = trimmed.split('|').filter(c => c.trim() !== '');
          out.push('<tr>');
          cells.forEach(c => out.push(`<td>${inlineFormat(c.trim())}</td>`));
          out.push('</tr>');
        }
        i++; continue;
      }
      closeTable();

      // Başlıklar
      if (trimmed.startsWith('### ')) { closeUl(); out.push(`<h3>${inlineFormat(trimmed.slice(4))}</h3>`); i++; continue; }
      if (trimmed.startsWith('## '))  { closeUl(); out.push(`<h2>${inlineFormat(trimmed.slice(3))}</h2>`); i++; continue; }
      if (trimmed.startsWith('# '))   { closeUl(); out.push(`<h1>${inlineFormat(trimmed.slice(2))}</h1>`); i++; continue; }

      // Liste
      if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
        if (!inUl) { out.push('<ul>'); inUl = true; }
        out.push(`<li>${inlineFormat(trimmed.slice(2))}</li>`);
        i++; continue;
      }
      closeUl();

      // Yatay çizgi
      if (trimmed.match(/^---+$/) || trimmed.match(/^\*\*\*+$/)) { out.push('<hr>'); i++; continue; }

      // Boş satır → paragraf ayracı
      if (trimmed === '') {
        out.push('<br>');
        i++; continue;
      }

      // Normal paragraf
      out.push(`<p>${inlineFormat(trimmed)}</p>`);
      i++;
    }
    closeUl();
    closeTable();

    return out.join('\n');
  }

  const renderedTr = $derived(rule ? markdownToHtml(rule.contentTr || rule.content || '') : '');
  const renderedKu = $derived(rule ? markdownToHtml(rule.content || '') : '');
  const showKu     = $derived(!!rule?.content && rule.content !== rule.contentTr);
</script>

<svelte:head>
  <title>{rule?.titleTr ?? 'Dilbilgisi'} — Kurdî</title>
</svelte:head>

<div class="grammar-page">
  <div class="grammar-topbar">
    <button class="back-btn" onclick={() => goto('/kurdi?tab=grammar')}>←</button>
    <span class="grammar-topbar-title">Dilbilgisi</span>
  </div>

  {#if loading}
    <div class="grammar-loading">Yükleniyor…</div>
  {:else if !rule}
    <div class="grammar-loading">Kural bulunamadı.</div>
  {:else}
    <div class="grammar-body">
      <h1 class="grammar-title">{rule.titleTr || rule.title}</h1>
      {#if rule.title && rule.titleTr && rule.title !== rule.titleTr}
        <p class="grammar-title-ku">{rule.title}</p>
      {/if}
      <div class="grammar-divider"></div>

      <!-- Türkçe içerik — Markdown render -->
      <div class="grammar-content md-body">{@html renderedTr}</div>

      <!-- Kürtçe içerik -->
      {#if showKu}
        <div class="grammar-section-label">Kurdî</div>
        <div class="grammar-content md-body ku">{@html renderedKu}</div>
      {/if}
    </div>
  {/if}
</div>

<style>
.grammar-page { min-height:100dvh; padding-bottom:80px; }
.grammar-topbar {
  display:flex; align-items:center; gap:10px; padding:10px 14px;
  background:var(--surface); border-bottom:1px solid var(--divider);
  position:sticky; top:0; z-index:10;
}
.back-btn { background:none; border:none; cursor:pointer; font-size:1.3rem; color:var(--on-bg); padding:4px; }
.grammar-topbar-title { font-size:.95rem; font-weight:700; }
.grammar-loading { display:flex; align-items:center; justify-content:center; height:160px; color:var(--muted); }
.grammar-body { padding:20px 16px; }
.grammar-title { font-size:1.3rem; font-weight:800; margin:0 0 4px; color:var(--on-bg); }
.grammar-title-ku { font-size:1rem; color:var(--primary); margin:0 0 12px; }
.grammar-divider { height:2px; background:var(--divider); margin-bottom:16px; border-radius:1px; }
.grammar-section-label {
  font-size:.72rem; font-weight:700; text-transform:uppercase;
  letter-spacing:.05em; color:var(--muted); margin:20px 0 8px;
}
.grammar-content { font-size:.92rem; line-height:1.8; color:var(--on-bg); }
.grammar-content.ku { color:var(--primary); }

/* Markdown içerik stilleri */
.md-body :global(h1) { font-size:1.15rem; font-weight:800; margin:16px 0 8px; color:var(--on-bg); }
.md-body :global(h2) { font-size:1.05rem; font-weight:700; margin:14px 0 6px; color:var(--on-bg); }
.md-body :global(h3) { font-size:.95rem;  font-weight:700; margin:12px 0 5px; color:var(--on-bg); }
.md-body :global(p)  { margin:6px 0; }
.md-body :global(strong) { font-weight:700; }
.md-body :global(em)     { font-style:italic; }
.md-body :global(code) {
  background:color-mix(in srgb,var(--primary) 10%,transparent);
  color:var(--primary); border-radius:4px; padding:2px 6px;
  font-family:monospace; font-size:.88em;
}
.md-body :global(ul) { margin:6px 0; padding-left:20px; }
.md-body :global(li) { margin:3px 0; }
.md-body :global(hr) { border:none; border-top:1px solid var(--divider); margin:14px 0; }
.md-body :global(a)  { color:var(--primary); text-decoration:underline; }
.md-body :global(br) { display:block; content:''; margin:6px 0; }

/* Tablo stilleri — Android GrammarHtmlTable karşılığı */
.md-body :global(.md-table) {
  width:100%; border-collapse:collapse; border-radius:10px;
  overflow:hidden; margin:10px 0;
  border:1px solid color-mix(in srgb,var(--primary) 25%,transparent);
}
.md-body :global(.md-table th) {
  padding:9px 12px; text-align:left; font-size:.82rem; font-weight:700;
  background:color-mix(in srgb,var(--primary) 15%,transparent); color:var(--on-bg);
  border-bottom:1px solid color-mix(in srgb,var(--primary) 25%,transparent);
}
.md-body :global(.md-table td) {
  padding:8px 12px; font-size:.82rem; color:var(--on-bg);
  border-bottom:1px solid var(--divider);
}
.md-body :global(.md-table tr:nth-child(even) td) {
  background:color-mix(in srgb,var(--primary) 4%,transparent);
}
.md-body :global(.md-table tr:last-child td) { border-bottom:none; }
</style>
