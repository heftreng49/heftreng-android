// Android DraftManager karşılığı — localStorage tabanlı
// Tek aktif taslak; 24 saat geçerliliği var.

const KEY_TEXT     = 'hf_draft_text';
const KEY_TITLE    = 'hf_draft_title';
const KEY_SAVED_AT = 'hf_draft_saved_at';
const MAX_AGE_MS   = 24 * 60 * 60 * 1000;

export function draftSave(text: string, title = ''): void {
  if (!text.trim()) return;
  try {
    localStorage.setItem(KEY_TEXT,     text);
    localStorage.setItem(KEY_TITLE,    title);
    localStorage.setItem(KEY_SAVED_AT, String(Date.now()));
  } catch {}
}

export function draftLoad(): { text: string; title: string } | null {
  try {
    const text    = localStorage.getItem(KEY_TEXT)     ?? '';
    const title   = localStorage.getItem(KEY_TITLE)    ?? '';
    const savedAt = Number(localStorage.getItem(KEY_SAVED_AT) ?? 0);
    if (!text.trim()) return null;
    if (Date.now() - savedAt > MAX_AGE_MS) { draftClear(); return null; }
    return { text, title };
  } catch { return null; }
}

export function draftClear(): void {
  try {
    localStorage.removeItem(KEY_TEXT);
    localStorage.removeItem(KEY_TITLE);
    localStorage.removeItem(KEY_SAVED_AT);
  } catch {}
}
