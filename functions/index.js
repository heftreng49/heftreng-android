/**
 * Heftreng — Cloud Functions v3
 * firebase-admin: 11.11.1
 * firebase-functions: 4.9.0 (v1 API)
 */

const functions = require("firebase-functions");
const admin     = require("firebase-admin");

admin.initializeApp();

// ── sendPush — HTTP Callable ──────────────────────────────────────────────────
exports.sendPush = functions
  .region("europe-west1")
  .https.onCall(async (data, context) => {

    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Giriş gerekli.");
    }

    const { targetUid, title, body, type, postId, fromUid, convId } = data;
    console.log("[HF Push] uid:", context.auth.uid, "→ target:", targetUid, "type:", type);

    if (!targetUid) {
      throw new functions.https.HttpsError("invalid-argument", "targetUid gerekli.");
    }

    const db = admin.firestore();

    let fcmToken = null;
    try {
      const doc = await db.collection("users").doc(targetUid).get();
      if (!doc.exists) {
        console.warn("[HF Push] Kullanıcı yok:", targetUid);
        return { success: false, reason: "user_not_found" };
      }
      fcmToken = doc.data().fcmToken || null;
      console.log("[HF Push] Token:", fcmToken ? "VAR" : "YOK");
    } catch (e) {
      console.error("[HF Push] Firestore hatası:", e.message);
      throw new functions.https.HttpsError("internal", "Kullanıcı okunamadı.");
    }

    if (!fcmToken) {
      console.warn("[HF Push] Token yok:", targetUid);
      return { success: false, reason: "no_token" };
    }

    const channelId = type === "message"
      ? "heftreng_messages"
      : (type === "like" || type === "repost")
        ? "heftreng_likes"
        : "heftreng_default";

    try {
      const result = await admin.messaging().send({
        token: fcmToken,
        notification: { title: title || "Heftreng", body: body || "" },
        android: {
          priority: "high",
          notification: { channelId, icon: "ic_notif", color: "#8B5CF6" },
          data: {
            type:    type    || "default",
            postId:  postId  || "",
            fromUid: fromUid || "",
            convId:  convId  || "",
          },
        },
      });
      console.log("[HF Push] ✓ Gönderildi:", result);
      return { success: true };
    } catch (err) {
      console.error("[HF Push] FCM hatası:", err.code, err.message);
      const STALE = [
        "messaging/registration-token-not-registered",
        "messaging/invalid-registration-token",
        "messaging/invalid-argument",
      ];
      if (STALE.includes(err.code)) {
        await db.collection("users").doc(targetUid).update({ fcmToken: null }).catch(() => {});
        return { success: false, reason: "stale_token" };
      }
      throw new functions.https.HttpsError("internal", err.message);
    }
  });

// ── onNewNotif — Firestore Trigger ────────────────────────────────────────────
// userNotifs/{uid}/msgs koleksiyonuna yeni belge eklenince çalışır
// Web tema → Android push köprüsü
exports.onNewNotif = functions
  .region("europe-west1")
  .firestore
  .document("userNotifs/{uid}/msgs/{msgId}")
  .onCreate(async (snap, context) => {
    const uid  = context.params.uid;
    const data = snap.data();

    console.log("[HF Notif] Yeni bildirim → uid:", uid, "type:", data.type);

    const db = admin.firestore();
    let fcmToken = null;
    try {
      const userDoc = await db.collection("users").doc(uid).get();
      if (!userDoc.exists) return null;
      fcmToken = userDoc.data().fcmToken || null;
    } catch (e) {
      console.error("[HF Notif] Kullanıcı okunamadı:", e.message);
      return null;
    }

    if (!fcmToken) {
      console.warn("[HF Notif] Token yok:", uid);
      return null;
    }

    const channelId = data.type === "message"
      ? "heftreng_messages"
      : (data.type === "like" || data.type === "repost")
        ? "heftreng_likes"
        : "heftreng_default";

    try {
      await admin.messaging().send({
        token: fcmToken,
        notification: {
          title: data.title || "Heftreng",
          body:  data.body  || data.sub || "",
        },
        android: {
          priority: "high",
          notification: { channelId, icon: "ic_notif", color: "#8B5CF6" },
          data: {
            type:    data.type    || "default",
            postId:  data.feedId  || data.postId || "",
            fromUid: data.fromUid || "",
            convId:  data.convId  || "",
          },
        },
      });
      console.log("[HF Notif] ✓ Push gönderildi → uid:", uid);
    } catch (err) {
      console.error("[HF Notif] FCM hatası:", err.code, err.message);
      if (err.code === "messaging/registration-token-not-registered") {
        await db.collection("users").doc(uid).update({ fcmToken: null }).catch(() => {});
      }
    }
    return null;
  });
