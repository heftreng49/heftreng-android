/**
 * Heftreng — Cloud Functions
 * firebase-functions v5 uyumlu
 */

const { onCall, HttpsError, onRequest } = require("firebase-functions/v2/https");
const { onDocumentCreated }             = require("firebase-functions/v2/firestore");
const functions                         = require("firebase-functions");
const { initializeApp }                 = require("firebase-admin/app");
const { getFirestore }                  = require("firebase-admin/firestore");
const { getMessaging }                  = require("firebase-admin/messaging");

initializeApp();

// ─── Yardımcı: Auth verisinden güvenli displayName üret ─────────────────────
function deriveName(authUser) {
  return (authUser.displayName || "").trim()
      || (authUser.email || "").split("@")[0].replace(/[._-]/g, " ").trim()
      || "Kullanıcı";
}

// ─── onNewNotif — Firestore Trigger (v2) ────────────────────────────────────
exports.onNewNotif = onDocumentCreated(
  { document: "userNotifs/{uid}/msgs/{msgId}", region: "europe-west1" },
  async (event) => {
    const uid  = event.params.uid;
    const data = event.data?.data();
    if (!data) { console.warn("[HF Trigger] Belge boş — uid:", uid); return; }

    const type    = data.type    || "default";
    const title   = data.title   || "Heftreng";
    const body    = data.sub     || data.message || "";
    const postId  = data.feedId  || data.postId  || "";
    const fromUid = data.fromUid || "";
    const convId  = data.convId  || "";

    let url = "https://heft-reng.blogspot.com/";
    if (type === "message") url = "https://heft-reng.blogspot.com/p/mesajlar.html";
    else if (postId)        url = "https://heft-reng.blogspot.com/p/akis_01024829108.html";

    const db = getFirestore();
    let fcmToken = null;
    try {
      const doc = await db.collection("users").doc(uid).get();
      if (!doc.exists) { console.log("[HF Trigger] Kullanıcı yok — uid:", uid); return; }
      fcmToken = doc.data()?.fcmToken || null;
    } catch (e) { console.error("[HF Trigger] Firestore hatası:", e.message); return; }

    if (!fcmToken || fcmToken.startsWith("https://")) {
      console.log("[HF Trigger] FCM token yok — uid:", uid); return;
    }

    const channelId =
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    const msg = {
      token: fcmToken,
      notification: { title, body },
      android: { priority: "high", notification: { channelId, icon: "ic_notif", color: "#8B5CF6" } },
      data: { type, postId, fromUid, convId, url },
    };
    const STALE = [
      "messaging/registration-token-not-registered",
      "messaging/invalid-registration-token",
      "messaging/invalid-argument",
    ];
    try {
      const result = await getMessaging().send(msg);
      console.log("[HF Trigger] ✓ FCM →", uid, result);
    } catch (err) {
      console.error("[HF Trigger] FCM hatası:", err.code, "→ uid:", uid);
      if (STALE.includes(err.code))
        await db.collection("users").doc(uid).update({ fcmToken: null }).catch(() => {});
    }
  }
);

// ─── sendPush — HTTPS Callable (v2) ─────────────────────────────────────────
exports.sendPush = onCall(
  { region: "europe-west1", cors: true, enforceAppCheck: false },
  async (request) => {
    const { targetUid, title = "Heftreng", body = "", type = "default",
            postId = "", fromUid = "", convId = "",
            url = "https://heft-reng.blogspot.com/" } = request.data || {};

    if (!targetUid) return { success: false, reason: "no_target" };

    const db = getFirestore();
    let userData;
    try {
      const doc = await db.collection("users").doc(targetUid).get();
      if (!doc.exists) return { success: false, reason: "user_not_found" };
      userData = doc.data();
    } catch (e) { throw new HttpsError("internal", "Kullanıcı okunamadı."); }

    const fcmToken = userData.fcmToken || null;
    if (!fcmToken) return { success: false, reason: "no_fcm_token" };
    if (fcmToken.startsWith("https://")) return { success: false, reason: "web_sub_not_supported" };

    const channelId =
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    try {
      const msg = {
        token: fcmToken,
        notification: { title, body },
        android: { priority: "high", notification: { channelId, icon: "ic_notif", color: "#8B5CF6" } },
        data: { type, postId, fromUid, convId, url },
      };
      const result = await getMessaging().send(msg);
      return { success: true, messageId: result };
    } catch (err) {
      const staleErrors = [
        "messaging/registration-token-not-registered",
        "messaging/invalid-registration-token",
        "messaging/invalid-argument",
      ];
      if (staleErrors.includes(err.code)) {
        await db.collection("users").doc(targetUid).update({ fcmToken: null }).catch(() => {});
        return { success: false, reason: "stale_token" };
      }
      throw new HttpsError("internal", err.message);
    }
  }
);

// ─── fixNewUserDisplayName — Auth onCreate (v1 gen1) ────────────────────────
exports.fixNewUserDisplayName = functions
  .runWith({})
  .region("us-central1")
  .auth.user()
  .onCreate(async (user) => {
    const db = getFirestore();
    try {
      const derived = deriveName(user);
      const base    = (user.email || "").split("@")[0]
                        .toLowerCase().replace(/[^a-z0-9]/g, "");

      await db.collection("users").doc(user.uid).set({
        uid        : user.uid,
        displayName: derived,
        name       : derived,
        username   : base || user.uid.slice(0, 8),
        email      : user.email    || "",
        photoURL   : user.photoURL || "",
        createdAt  : new Date(),
        followers  : 0,
        following  : 0,
        postCount  : 0,
      }, { merge: true });

      console.log(`[fixNewUser] ✓ ${user.uid} → "${derived}"`);
    } catch (e) {
      console.error("[fixNewUser] hata:", e);
    }
  });

// ─── repairAllUsers — HTTP endpoint ─────────────────────────────────────────
// Auth'taki TÜM kullanıcıları tarar:
//   - Firestore belgesi yoksa → oluşturur
//   - Firestore belgesi varsa ama eksik alanlar varsa → tamamlar
// Kullanım: GET .../repairAllUsers?secret=hf2024
exports.repairAllUsers = onRequest(async (req, res) => {
  if (req.query.secret !== "hf2024") {
    res.status(403).json({ error: "Yetkisiz" }); return;
  }

  const db   = getFirestore();
  const auth = require("firebase-admin/auth").getAuth();

  let created = 0, patched = 0, skipped = 0, errors = 0;
  const details = [];

  try {
    // Auth'taki tüm kullanıcıları sayfalı çek
    let pageToken;
    do {
      const listResult = await auth.listUsers(1000, pageToken);
      pageToken = listResult.pageToken;

      for (const authUser of listResult.users) {
        try {
          const ref  = db.collection("users").doc(authUser.uid);
          const snap = await ref.get();
          const derived = deriveName(authUser);
          const base    = (authUser.email || "").split("@")[0]
                            .toLowerCase().replace(/[^a-z0-9]/g, "");

          if (!snap.exists) {
            // Belge hiç yok — sıfırdan oluştur
            await ref.set({
              uid        : authUser.uid,
              displayName: derived,
              name       : derived,
              username   : base || authUser.uid.slice(0, 8),
              email      : authUser.email    || "",
              photoURL   : authUser.photoURL || "",
              createdAt  : new Date(),
              followers  : 0,
              following  : 0,
              postCount  : 0,
            });
            created++;
            details.push({ uid: authUser.uid, status: "created", name: derived });
            console.log(`[repairAll] ✓ OLUŞTURULDU: ${authUser.uid} → "${derived}"`);

          } else {
            // Belge var — eksik alanları tamamla
            const data  = snap.data();
            const patch = {};

            if (!data.displayName || data.displayName === "Kullanıcı")
              patch.displayName = derived;
            if (!data.name || data.name === "Kullanıcı")
              patch.name = derived;
            if (!data.photoURL && authUser.photoURL)
              patch.photoURL = authUser.photoURL;
            if (!data.email && authUser.email)
              patch.email = authUser.email;
            if (!data.uid)
              patch.uid = authUser.uid;
            if (!data.username)
              patch.username = base || authUser.uid.slice(0, 8);
            if (data.followers  === undefined) patch.followers  = 0;
            if (data.following  === undefined) patch.following  = 0;
            if (data.postCount  === undefined) patch.postCount  = 0;
            if (!data.createdAt) patch.createdAt = new Date();

            if (Object.keys(patch).length > 0) {
              await ref.update(patch);
              patched++;
              details.push({ uid: authUser.uid, status: "patched", fields: Object.keys(patch) });
              console.log(`[repairAll] ✓ GÜNCELLENDI: ${authUser.uid}`, Object.keys(patch));
            } else {
              skipped++;
            }
          }
        } catch (e) {
          errors++;
          details.push({ uid: authUser.uid, status: "error", error: e.message });
          console.error(`[repairAll] hata — ${authUser.uid}:`, e.message);
        }
      }
    } while (pageToken);

    res.json({ created, patched, skipped, errors, details });

  } catch (e) {
    console.error("[repairAll] genel hata:", e);
    res.status(500).json({ error: e.message });
  }
});
