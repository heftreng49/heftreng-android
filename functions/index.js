/**
 * Heftreng — Cloud Functions
 * Deploy: firebase deploy --only functions
 *
 * v2 — onNewNotif Firestore trigger eklendi
 * v3 — fixNewUserDisplayName: Auth onCreate (v1) ile kullanıcı profili otomatik oluşturuluyor
 *      fixExistingUserNames: Mevcut "Kullanıcı" yazanları toplu düzelt (HTTP)
 */

const { onCall, HttpsError, onRequest } = require("firebase-functions/v2/https");
const { onDocumentCreated }             = require("firebase-functions/v2/firestore");
const functions                         = require("firebase-functions");   // v1 — Auth trigger için
const { initializeApp }                 = require("firebase-admin/app");
const { getFirestore }                  = require("firebase-admin/firestore");
const { getMessaging }                  = require("firebase-admin/messaging");

initializeApp();

// ─────────────────────────────────────────────────────────────────────────────
// onNewNotif — Firestore Trigger
// ─────────────────────────────────────────────────────────────────────────────
exports.onNewNotif = onDocumentCreated(
  {
    document: "userNotifs/{uid}/msgs/{msgId}",
    region  : "europe-west1",
  },
  async (event) => {
    const uid  = event.params.uid;
    const data = event.data?.data();

    if (!data) {
      console.warn("[HF Trigger] Belge verisi boş — uid:", uid);
      return;
    }

    const type    = data.type    || "default";
    const title   = data.title   || "Heftreng";
    const body    = data.sub     || data.message || "";
    const postId  = data.feedId  || data.postId  || "";
    const fromUid = data.fromUid || "";
    const convId  = data.convId  || "";

    let url = "https://heft-reng.blogspot.com/";
    if (type === "message") {
      url = "https://heft-reng.blogspot.com/p/mesajlar.html";
    } else if (postId) {
      url = "https://heft-reng.blogspot.com/p/akis_01024829108.html";
    }

    const db = getFirestore();

    let fcmToken = null;
    try {
      const doc = await db.collection("users").doc(uid).get();
      if (!doc.exists) {
        console.log("[HF Trigger] Kullanıcı bulunamadı — uid:", uid);
        return;
      }
      fcmToken = doc.data()?.fcmToken || null;
    } catch (e) {
      console.error("[HF Trigger] Firestore okuma hatası:", e.message);
      return;
    }

    if (!fcmToken || fcmToken.startsWith("https://")) {
      console.log("[HF Trigger] FCM token yok, push atlandı — uid:", uid);
      return;
    }

    const channelId =
      type === "message"                         ? "heftreng_messages" :
      type === "like" || type === "repost"       ? "heftreng_likes"    :
      "heftreng_default";

    const msg = {
      token: fcmToken,
      notification: { title, body },
      android: {
        priority: "high",
        notification: {
          channelId,
          icon : "ic_notif",
          color: "#8B5CF6",
        },
      },
      data: { type, postId, fromUid, convId, url },
    };

    const STALE = [
      "messaging/registration-token-not-registered",
      "messaging/invalid-registration-token",
      "messaging/invalid-argument",
    ];

    try {
      const result = await getMessaging().send(msg);
      console.log("[HF Trigger] ✓ FCM gönderildi →", uid, "| messageId:", result);
    } catch (err) {
      console.error("[HF Trigger] FCM hatası:", err.code, err.message, "→ uid:", uid);
      if (STALE.includes(err.code)) {
        await db.collection("users").doc(uid)
          .update({ fcmToken: null }).catch(() => {});
        console.log("[HF Trigger] Eski token temizlendi — uid:", uid);
      }
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// sendPush — HTTPS Callable
// ─────────────────────────────────────────────────────────────────────────────
exports.sendPush = onCall(
  {
    region        : "europe-west1",
    cors          : true,
    enforceAppCheck: false,
  },
  async (request) => {
    const callerUid = request.auth?.uid || "anonymous";
    console.log("[HF Push] Çağrıldı — caller:", callerUid);

    const {
      targetUid,
      title   = "Heftreng",
      body    = "",
      type    = "default",
      postId  = "",
      fromUid = "",
      convId  = "",
      url     = "https://heft-reng.blogspot.com/",
    } = request.data || {};

    if (!targetUid) {
      console.warn("[HF Push] targetUid eksik");
      return { success: false, reason: "no_target" };
    }

    const db = getFirestore();

    let userData;
    try {
      const doc = await db.collection("users").doc(targetUid).get();
      if (!doc.exists) {
        console.warn("[HF Push] Kullanıcı bulunamadı:", targetUid);
        return { success: false, reason: "user_not_found" };
      }
      userData = doc.data();
    } catch (e) {
      console.error("[HF Push] Firestore okuma hatası:", e.message);
      throw new HttpsError("internal", "Kullanıcı okunamadı.");
    }

    const fcmToken = userData.fcmToken || null;

    if (!fcmToken) {
      console.warn("[HF Push] FCM token yok — uid:", targetUid);
      return { success: false, reason: "no_fcm_token" };
    }

    if (fcmToken.startsWith("https://")) {
      console.warn("[HF Push] Web push subscription — FCM desteklenmiyor");
      return { success: false, reason: "web_sub_not_supported" };
    }

    const channelId =
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    try {
      const msg = {
        token: fcmToken,
        notification: { title, body },
        android: {
          priority: "high",
          notification: {
            channelId,
            icon : "ic_notif",
            color: "#8B5CF6",
          },
        },
        data: { type, postId, fromUid, convId, url },
      };

      const result = await getMessaging().send(msg);
      console.log("[HF Push] ✓ FCM gönderildi — messageId:", result);
      return { success: true, messageId: result };

    } catch (err) {
      console.error("[HF Push] FCM hatası:", err.code, err.message);

      const staleErrors = [
        "messaging/registration-token-not-registered",
        "messaging/invalid-registration-token",
        "messaging/invalid-argument",
      ];
      if (staleErrors.includes(err.code)) {
        await db.collection("users").doc(targetUid)
          .update({ fcmToken: null }).catch(() => {});
        return { success: false, reason: "stale_token" };
      }

      throw new HttpsError("internal", err.message);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// fixNewUserDisplayName — Auth onCreate (v1 syntax, GCIP gerektirmez)
//
// Kullanıcı kayıt olunca otomatik çalışır:
//   - displayName boşsa email'den üretir (örn. ali.veli@... → "ali veli")
//   - users/{uid} belgesini oluşturur (merge: true)
// ─────────────────────────────────────────────────────────────────────────────
exports.fixNewUserDisplayName = functions
  .region("us-east1")
  .auth.user()
  .onCreate(async (user) => {
    const db = getFirestore();
    try {
      const derived =
        user.displayName?.trim() ||
        user.email?.split("@")[0]?.replace(/[._]/g, " ") ||
        "Kullanıcı";

      await db.collection("users").doc(user.uid).set(
        {
          uid        : user.uid,
          displayName: derived,
          name       : derived,
          email      : user.email || "",
          photoURL   : user.photoURL || "",
          createdAt  : new Date(),
        },
        { merge: true }
      );

      console.log(`[fixNewUser] ${user.uid} → "${derived}"`);
    } catch (e) {
      console.error("[fixNewUser] hata:", e);
    }
  });

// ─────────────────────────────────────────────────────────────────────────────
// fixExistingUserNames — HTTP trigger (bir kez çalıştır)
//
// Firestore'da displayName = "Kullanıcı" veya boş olan herkesi düzeltir.
// Çalıştırmak için: https://us-central1-bloggerheftreng.cloudfunctions.net/fixExistingUserNames
// ─────────────────────────────────────────────────────────────────────────────
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
      const derived  =
        authUser.displayName?.trim() ||
        authUser.email?.split("@")[0]?.replace(/[._]/g, " ") ||
        null;

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
