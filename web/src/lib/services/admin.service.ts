// Android AdminViewModel karşılığı
// Firestore: admins, users, feed, reports, appeals, userNotifs, moderationLogs
import {
  collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc,
  query, where, orderBy, limit, addDoc, serverTimestamp, writeBatch,
  getCountFromServer,
} from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';

// ── İzin sistemi (Android roleToPermissions karşılığı) ──────────────────────
export const ROLE_PERMS: Record<string, string[]> = {
  superadmin: ['push','notif','users','pending','reports','appeals','stats','edit','library','kurdi','staff'],
  admin:      ['push','notif','users','pending','reports','appeals','stats','edit','library','kurdi'],
  moderator:  ['reports','appeals','users'],
  editor:     ['pending','edit','library'],
  kurdi_admin:['kurdi'],
};

export interface AdminPerms {
  role:  string;
  title: string;
  perms: string[];
  can:   (key: string) => boolean;
  isStaff: () => boolean;
}

export async function checkAdminPerms(uid: string): Promise<AdminPerms | null> {
  const snap = await getDoc(doc(db, 'admins', uid));
  if (!snap.exists()) return null;
  const d = snap.data();
  const role  = d.role  ?? 'moderator';
  const perms = (d.permissions as string[]) ?? ROLE_PERMS[role] ?? [];
  return {
    role, title: d.title ?? role,
    perms,
    can:     (k) => perms.includes(k),
    isStaff: () => perms.length > 0,
  };
}

// ── İstatistikler ────────────────────────────────────────────────────────────
export async function fetchStats() {
  const cached = await getDoc(doc(db, 'appConfig', 'stats'));
  if (cached.exists()) return cached.data();

  const todayTs = new Date(); todayTs.setHours(0,0,0,0);
  const [usersSnap, postsSnap, onlineSnap] = await Promise.all([
    getCountFromServer(query(collection(db, 'users'), where('createdAt', '>=', todayTs.getTime()))),
    getCountFromServer(query(collection(db, 'feed'), where('moderationStatus', '==', 'active'))),
    getCountFromServer(query(collection(db, 'presence'), where('online', '==', true))),
  ]);
  return {
    newUsersToday: usersSnap.data().count,
    newPostsToday: postsSnap.data().count,
    onlineNow:     onlineSnap.data().count,
  };
}

// ── Kullanıcı yönetimi ───────────────────────────────────────────────────────
export async function fetchUsers(search = '', page = 0, pageSize = 20) {
  const { data } = await supabase.from('users')
    .select('uid,display_name,username,photo_url,email,banned,followers_count,created_at')
    .ilike(search ? 'display_name' : 'uid', search ? `%${search}%` : '%')
    .order('created_at', { ascending: false })
    .range(page * pageSize, (page + 1) * pageSize - 1);
  return data ?? [];
}

export async function banUser(uid: string, reason: string, adminUid: string): Promise<void> {
  await updateDoc(doc(db, 'users', uid), { banned: true, banReason: reason, bannedAt: serverTimestamp(), bannedBy: adminUid });
  await supabase.from('users').update({ banned: true }).eq('uid', uid);
}

export async function unbanUser(uid: string): Promise<void> {
  await updateDoc(doc(db, 'users', uid), { banned: false, banReason: '', bannedAt: null, bannedBy: null });
  await supabase.from('users').update({ banned: false }).eq('uid', uid);
}

export async function changeUserRole(uid: string, role: string, title: string): Promise<void> {
  const perms = ROLE_PERMS[role] ?? [];
  await setDoc(doc(db, 'admins', uid), { role, title, permissions: perms, updatedAt: serverTimestamp() }, { merge: true });
}

// ── Moderasyon (şikayetler) ──────────────────────────────────────────────────
export async function fetchReports() {
  const snap = await getDocs(query(
    collection(db, 'reports'),
    where('status', '==', 'pending'),
    orderBy('createdAt', 'desc'), limit(50),
  ));
  return snap.docs.map(d => ({ id: d.id, ...d.data() }));
}

export async function updateReportStatus(reportId: string, status: string): Promise<void> {
  await updateDoc(doc(db, 'reports', reportId), { status, resolvedAt: serverTimestamp() });
}

export async function moderatePost(
  postId: string, targetUid: string, targetName: string,
  status: string, reason: string, adminNote: string,
): Promise<void> {
  await updateDoc(doc(db, 'feed', postId), {
    moderationStatus: status, moderationReason: reason,
    moderatedAt: serverTimestamp(), adminNote,
  });
  const titles: Record<string,string> = {
    restricted: 'Gönderiniz kısıtlandı',
    suspended:  'Gönderiniz askıya alındı',
    removed:    'Gönderiniz kaldırıldı',
  };
  await addDoc(collection(db, 'userNotifs', targetUid, 'msgs'), {
    type: 'moderation', title: titles[status] ?? 'Gönderi durumu güncellendi',
    message: reason || titles[status] || '', feedId: postId,
    fromUid: 'system', fromName: 'Heftreng', fromPhoto: '',
    read: false, ts: serverTimestamp(), url: `/post/${postId}`,
  });
  await addDoc(collection(db, 'moderationLogs'), {
    postId, targetUid, targetName, status, reason, adminNote, ts: serverTimestamp(),
  });
}

export async function restorePost(postId: string, targetUid: string): Promise<void> {
  await updateDoc(doc(db, 'feed', postId), { moderationStatus: 'active', moderationReason: '' });
  await addDoc(collection(db, 'userNotifs', targetUid, 'msgs'), {
    type: 'moderation', title: 'Gönderiniz yeniden aktif edildi',
    message: 'Gönderiniz yayına alındı.', feedId: postId,
    fromUid: 'system', fromName: 'Heftreng', fromPhoto: '',
    read: false, ts: serverTimestamp(), url: `/post/${postId}`,
  });
}

// ── İtirazlar ────────────────────────────────────────────────────────────────
export async function fetchAppeals() {
  const snap = await getDocs(query(
    collection(db, 'appeals'),
    where('status', '==', 'pending'),
    orderBy('createdAt', 'desc'), limit(50),
  ));
  return snap.docs.map(d => ({ id: d.id, ...d.data() }));
}

export async function resolveAppeal(
  appeal: any, approved: boolean, adminNote = '',
): Promise<void> {
  await updateDoc(doc(db, 'appeals', appeal.id), {
    status: approved ? 'approved' : 'rejected',
    adminNote, resolvedAt: serverTimestamp(),
  });
  if (approved) await restorePost(appeal.postId, appeal.postOwnerUid);
  const title = approved ? 'İtirazınız kabul edildi' : 'İtirazınız reddedildi';
  const msg   = approved
    ? `Gönderiniz yeniden aktif edildi. ${adminNote}`
    : `Kararımız geçerliliğini korumaktadır. ${adminNote}`;
  await addDoc(collection(db, 'userNotifs', appeal.postOwnerUid, 'msgs'), {
    type: 'appeal_result', title, message: msg,
    feedId: appeal.postId, postId: appeal.postId,
    fromUid: 'system', fromName: 'Heftreng', fromPhoto: '',
    read: false, ts: serverTimestamp(),
  });
}

// ── Push bildirimi ───────────────────────────────────────────────────────────
export async function sendPushNotification(
  title: string, body: string, url = '',
): Promise<{ success: boolean; message: string }> {
  // FCM veya kendi notification collection'a yaz
  try {
    await addDoc(collection(db, 'notifications'), {
      title, body, url,
      sentAt: serverTimestamp(), type: 'push',
    });
    return { success: true, message: 'Bildirim kuyruğa eklendi.' };
  } catch(e: any) {
    return { success: false, message: e.message ?? 'Hata.' };
  }
}

// ── Personel yönetimi ─────────────────────────────────────────────────────────
export async function fetchStaff() {
  const snap = await getDocs(query(collection(db, 'admins'), limit(50)));
  return Promise.all(snap.docs.map(async d => {
    const data = d.data();
    let name = data.displayName ?? '';
    if (!name) {
      try {
        const u = await getDoc(doc(db, 'users', d.id));
        name = u.data()?.displayName ?? d.id;
      } catch {}
    }
    return { uid: d.id, name, ...data };
  }));
}

export async function addStaff(
  uid: string, title: string, role: string,
): Promise<void> {
  const perms = ROLE_PERMS[role] ?? [];
  await setDoc(doc(db, 'admins', uid.trim()), {
    role, title, permissions: perms, createdAt: serverTimestamp(),
  });
}

export async function removeStaff(uid: string): Promise<void> {
  await deleteDoc(doc(db, 'admins', uid));
}
