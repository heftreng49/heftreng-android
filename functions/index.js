/**
 * Heftreng — Cloud Functions
 * firebase-functions v5 uyumlu
 * fixNewUserDisplayName: functions.runWith() ile gen1 Auth trigger
 */

const { onCall, HttpsError, onRequest } = require("firebase-functions/v2/https");
const { onDocumentCreated }             = require("firebase-functions/v2/firestore");
const functions                         = require("firebase-functions");
const { initializeApp }                 = require("firebase-admin/app");
const { getFirestore }                  = require("firebase-admin/firestore");
const { getMessaging }                  = require("firebase-admin/messaging");

initializeApp();

// ─── onNewNotif — Firestore Trigger (v2) ────────────────────────────────────
exports.onNewNotif = onDocumentCreated(
  {
    document: "userNotifs/{uid}/msgs/{msgId}",
    region  : "europe-west1",
  },
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
      if (STALE.includes(err.code)) {
        await db.collection("users").doc(uid).update({ fcmToken: null }).catch(() => {});
      }
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
      console.log("[HF Push] ✓ FCM gönderildi:", result);
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

// ─── fixNewUserDisplayName — Auth onCreate (v1, gen1) ───────────────────────
// runWith({}) ile açıkça gen1 olarak işaretlenir → firebase-tools CPU hatası almaz
exports.fixNewUserDisplayName = functions
  .runWith({})
  .region("us-central1")
  .auth.user()
  .onCreate(async (user) => {
    const db = getFirestore();
    try {
      const derived = user.displayName?.trim()
                   || user.email?.split("@")[0]?.replace(/[._]/g, " ")
                   || "Kullanıcı";

      await db.collection("users").doc(user.uid).set({
        uid        : user.uid,
        displayName: derived,
        name       : derived,
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

// ─── fixExistingUserNames — HTTP (bir kez çalıştır) ─────────────────────────
exports.fixExistingUserNames = onRequest(async (req, res) => {
  const db   = getFirestore();
  const auth = require("firebase-admin/auth").getAuth();
  let fixed = 0, skipped = 0;

  const snap = await db.collection("users").get();
  for (const doc of snap.docs) {
    const data = doc.data();
    const name = data.displayName || data.name || "";
    if (name && name !== "Kullanıcı") { skipped++; continue; }

    try {
      const authUser = await auth.getUser(doc.id);
      const derived  = authUser.displayName?.trim()
                    || authUser.email?.split("@")[0]?.replace(/[._]/g, " ")
                    || null;
      if (!derived || derived === "Kullanıcı") { skipped++; continue; }
      await doc.ref.update({ displayName: derived, name: derived });
      fixed++;
      console.log(`[fixExisting] ${doc.id} → "${derived}"`);
    } catch (e) {
      console.error(`[fixExisting] ${doc.id}:`, e.message);
      skipped++;
    }
  }
  res.json({ fixed, skipped, total: snap.size });
});
