// Android KurdiViewModel karşılığı
// Firestore: kf_units, kf_lessons, kf_grammar, kf_dict
// User progress: users/{uid}/kf_progress/{lessonId}

import {
  collection, query, orderBy, limit, getDocs,
  doc, getDoc, setDoc, updateDoc, increment, serverTimestamp,
} from 'firebase/firestore';
import { db } from '$lib/firebase/config';

// ── Veri modelleri ────────────────────────────────────────────────────────────
export interface KfUnit {
  id:      string;
  ttl:     string;   // Türkçe başlık (ttl veya nameTr)
  nameKu:  string;
  desc:    string;
  icon:    string;
  color:   string;
  order:   number;
}

export interface KfLesson {
  id:        string;
  unitId:    string;
  nameTr:    string;
  nameKu:    string;
  emoji:     string;
  xp:        number;
  order:     number;
  tip:       string;
  completed: boolean;
}

export interface KfVocab {
  id: string;
  ku: string;   // Kürtçe
  kp: string;   // telaffuz
  tr: string;   // Türkçe
  e:  string;   // emoji
}

export interface KfExercise {
  id:         string;
  type:       'mcq' | 'fill' | 'match' | 'build';
  question:   string;
  questionTr: string;
  optA: string; optB: string; optC: string; optD: string;
  answer:     string;
  wrong:      string[];
  pairs:      { a: string; b: string }[];
  words:      string[];
  tr:         string;
}

export interface GrammarRule {
  id:        string;
  title:     string;
  titleTr:   string;
  content:   string;
  contentTr: string;
  order:     number;
}

// XP hesaplama — Android KfExerciseXp karşılığı
export const XP_PER_TYPE: Record<string, number> = {
  fill: 2, build: 3, mcq: 2, match: 2,
};

// ── Üniteler + dersler ────────────────────────────────────────────────────────
export async function fetchUnitsAndLessons(uid?: string): Promise<{
  units:   KfUnit[];
  lessons: KfLesson[];
}> {
  const [unitsSnap, lessonsSnap] = await Promise.all([
    getDocs(query(collection(db, 'kf_units'),   orderBy('order'),  limit(100))),
    getDocs(query(collection(db, 'kf_lessons'), orderBy('order'),  limit(100))),
  ]);

  // Tamamlanan ders ID'leri
  let doneIds = new Set<string>();
  if (uid) {
    const progSnap = await getDocs(collection(db, 'users', uid, 'kf_progress'));
    progSnap.forEach(d => doneIds.add(d.id));
  }

  let units = unitsSnap.docs.map(d => {
    const x = d.data();
    return {
      id: d.id, order: x.order ?? 0, icon: x.icon ?? '📖', color: x.color ?? '#8B5CF6',
      ttl: x.ttl || x.nameTr || x.name || 'Ünite',
      nameKu: x.nameKu ?? '', desc: x.descTr || x.desc || '',
    } as KfUnit;
  }).sort((a, b) => a.order - b.order);

  let lessons = lessonsSnap.docs.map(d => {
    const x = d.data();
    return {
      id: d.id, unitId: x.unitId ?? '', order: x.order ?? 0,
      nameTr: x.nameTr || x.title || x.name || '',
      nameKu: x.nameKu || x.nameKmr || '',
      emoji: x.emoji ?? '📖', xp: x.xp ?? 10, tip: x.tip ?? '',
      completed: doneIds.has(d.id),
    } as KfLesson;
  }).sort((a, b) => a.unitId.localeCompare(b.unitId) || a.order - b.order);

  // kf_units boşsa varsayılan ünite oluştur
  if (units.length === 0 && lessons.length > 0) {
    units   = [{ id: 'u_default', ttl: 'Dersler', nameKu: '', desc: '', icon: '📚', color: '#8B5CF6', order: 0 }];
    lessons = lessons.map(l => ({ ...l, unitId: 'u_default' }));
  }

  return { units, lessons };
}

// ── Ders içeriği (vocab + exercises) ─────────────────────────────────────────
export async function fetchLessonContent(lessonId: string): Promise<{
  lesson:    KfLesson | null;
  vocab:     KfVocab[];
  exercises: KfExercise[];
}> {
  const lessonSnap = await getDoc(doc(db, 'kf_lessons', lessonId));
  if (!lessonSnap.exists()) return { lesson: null, vocab: [], exercises: [] };

  const [vocabSnap, exSnap] = await Promise.all([
    getDocs(query(collection(db, 'kf_lessons', lessonId, 'vocab'),     orderBy('order'))),
    getDocs(query(collection(db, 'kf_lessons', lessonId, 'exercises'), orderBy('order'))),
  ]);

  const x = lessonSnap.data();
  const lesson: KfLesson = {
    id: lessonSnap.id, unitId: x.unitId ?? '', order: x.order ?? 0,
    nameTr: x.nameTr || x.title || '', nameKu: x.nameKu || '',
    emoji: x.emoji ?? '📖', xp: x.xp ?? 10, tip: x.tip ?? '', completed: false,
  };

  const vocab = vocabSnap.docs.map(d => ({ id: d.id, ...d.data() } as KfVocab));
  const exercises = exSnap.docs.map(d => {
    const e = d.data();
    return {
      id: d.id, type: e.type ?? 'mcq',
      question: e.question ?? '', questionTr: e.questionTr ?? '',
      optA: e.optA ?? '', optB: e.optB ?? '', optC: e.optC ?? '', optD: e.optD ?? '',
      answer: e.answer ?? '', wrong: e.wrong ?? [],
      pairs: e.pairs ?? [], words: e.words ?? [], tr: e.tr ?? '',
    } as KfExercise;
  });

  return { lesson, vocab, exercises };
}

// ── Ders tamamla — XP + progress kaydet ──────────────────────────────────────
export async function completeLesson(
  uid: string, lessonId: string, lessonName: string,
  correctCount: number, exercises: KfExercise[],
): Promise<number> {
  // Android KfExerciseXp mantığı — her doğru cevap tipi için XP
  const earnedXp = exercises.slice(0, correctCount).reduce(
    (sum, ex) => sum + (XP_PER_TYPE[ex.type] ?? 2), 0
  );

  await Promise.all([
    // Progress kaydet
    setDoc(doc(db, 'users', uid, 'kf_progress', lessonId), {
      lessonId, lessonName, completedAt: serverTimestamp(),
      xpEarned: earnedXp,
    }),
    // Kullanıcı XP güncelle
    updateDoc(doc(db, 'users', uid), {
      xp:     increment(earnedXp),
      streak: increment(0),  // streak ayrı logic ile hesaplanır
    }),
  ]);

  return earnedXp;
}

// ── Dilbilgisi kuralları ──────────────────────────────────────────────────────
export async function fetchGrammarRules(): Promise<GrammarRule[]> {
  const snap = await getDocs(query(collection(db, 'kf_grammar'), orderBy('order'), limit(100)));
  return snap.docs.map(d => {
    const x = d.data();
    return {
      id: d.id, order: x.order ?? 0,
      title: x.title ?? '', titleTr: x.titleTr ?? '',
      content: x.content ?? '', contentTr: x.contentTr ?? '',
    } as GrammarRule;
  });
}

// ── Tamamlanan ders sayısı ────────────────────────────────────────────────────
export async function fetchCompletedLessons(uid: string): Promise<string[]> {
  const snap = await getDocs(collection(db, 'users', uid, 'kf_progress'));
  return snap.docs.map(d => d.id);
}
