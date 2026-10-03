// Android FeedViewModel.recordDailyActivityAndStreak karşılığı
// Günde bir kez çalışır; streak hesaplayıp Firestore users dokümanını günceller.
import { doc, updateDoc } from 'firebase/firestore';
import { db }             from '$lib/firebase/config';
import { supabase }       from '$lib/supabase/config';

const STORAGE_KEY = (uid: string) => `hf_last_streak_${uid}`;

// ── Günlük aktivite kaydet + streak güncelle ────────────────────────────────
export async function recordDailyActivityAndStreak(uid: string): Promise<void> {
  const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
  const lastDate = localStorage.getItem(STORAGE_KEY(uid)) ?? '';
  if (lastDate === today) return; // Bugün zaten çalıştı

  try {
    // Supabase daily_activity tablosuna upsert
    await supabase.from('daily_activity').upsert(
      { uid, date: today },
      { onConflict: 'uid,date' },
    );

    // Son 100 günlük aktiviteyi çekip streak hesapla
    const { data } = await supabase
      .from('daily_activity')
      .select('date')
      .eq('uid', uid)
      .order('date', { ascending: false })
      .limit(100);

    const streak = computeStreak((data ?? []).map((r: any) => r.date as string));

    // Firestore users dokümanını güncelle
    await updateDoc(doc(db, 'users', uid), {
      streak,
      lastStreakDate: today,
    });

    // Rozet kontrolü (client-side lightweight)
    await checkAndAwardStreakBadge(uid, streak);

    localStorage.setItem(STORAGE_KEY(uid), today);
  } catch (e) {
    console.warn('recordDailyActivityAndStreak:', e);
  }
}

// ── Ardışık gün sayısını hesapla ────────────────────────────────────────────
function computeStreak(dates: string[]): number {
  if (!dates.length) return 0;
  const sorted = [...new Set(dates)].sort().reverse(); // desc, unique
  let streak = 1;
  for (let i = 1; i < sorted.length; i++) {
    const prev = new Date(sorted[i - 1]);
    const curr = new Date(sorted[i]);
    const diff = (prev.getTime() - curr.getTime()) / 86_400_000;
    if (Math.round(diff) === 1) streak++;
    else break;
  }
  return streak;
}

// ── Streak rozeti ver ────────────────────────────────────────────────────────
async function checkAndAwardStreakBadge(uid: string, streak: number): Promise<void> {
  const milestones: Record<number, string> = { 7: 'streak_7', 30: 'streak_30', 100: 'streak_100' };
  const badge = milestones[streak];
  if (!badge) return;
  try {
    await supabase.from('user_badges').upsert(
      { uid, badge_id: badge },
      { onConflict: 'uid,badge_id' },
    );
  } catch {}
}
