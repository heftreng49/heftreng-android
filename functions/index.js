/**
 * Heftreng — Cloud Functions
 * firebase-admin: ^12.0.0
 * firebase-functions: ^5.0.0  (gen 2, admin 12 uyumlu)
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated }  = require("firebase-functions/v2/firestore");
const { initializeApp }      = require("firebase-admin/app");
const { getFirestore }       = require("firebase-admin/firestore");
const { getMessaging }       = require("firebase-admin/messaging");

initializeApp();

// ── sendPush — HTTPS Callable ─────────────────────────────────────────────────
exports.sendPush = onCall(
  { region: "europe-west1", enforceAppCheck: false },
  async (request) => {
    // firebase-functions v5: request.data
    const {
      targetUid,
      title   = "Heftreng",
      body    = "",
      type    = "default",
      postId  = "",
      fromUid = "",
      convId  = "",
    } = request.data || {};

    if (!targetUid) return { success: false, reason: "no_target" };

    const db = getFirestore();
    let fcmToken = null;
    try {
      const doc = await db.collection("users").doc(targetUid).get();
      if (!doc.exists) return { success: false, reason: "user_not_found" };
      fcmToken = doc.data()?.fcmToken || null;
    } catch (e) {
      throw new HttpsError("internal", "Kullanıcı okunamadı.");
    }

    if (!fcmToken || fcmToken.startsWith("https://"))
      return { success: false, reason: "no_token" };

    const channelId =
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    try {
      const result = await getMessaging().send({
        token: fcmToken,
        notification: { title, body },
        android: {
          priority: "high",
          notification: { channelId, icon: "ic_notif", color: "#8B5CF6" },
        },
        data: { type, postId, fromUid, convId },
      });
      console.log("[HF Push] ✓", result);
      return { success: true };
    } catch (err) {
      console.error("[HF Push] FCM hatası:", err.code, err.message);
      const STALE = [
        "messaging/registration-token-not-registered",
        "messaging/invalid-registration-token",
        "messaging/invalid-argument",
      ];
      if (STALE.includes(err.code)) {
        await db.collection("users").doc(targetUid)
          .update({ fcmToken: null }).catch(() => {});
        return { success: false, reason: "stale_token" };
      }
      throw new HttpsError("internal", err.message);
    }
  }
);

// ── onNewNotif — Firestore Trigger ────────────────────────────────────────────
exports.onNewNotif = onDocumentCreated(
  { document: "userNotifs/{uid}/msgs/{msgId}", region: "europe-west1" },
  async (event) => {
    const uid  = event.params.uid;
    const data = event.data?.data();
    if (!data) return;

    const db = getFirestore();
    let fcmToken = null;
    try {
      const doc = await db.collection("users").doc(uid).get();
      if (!doc.exists) return;
      fcmToken = doc.data()?.fcmToken || null;
    } catch (e) { return; }

    if (!fcmToken || fcmToken.startsWith("https://")) return;

    const channelId =
      data.type === "message"                         ? "heftreng_messages" :
      data.type === "like" || data.type === "repost"  ? "heftreng_likes"    :
      "heftreng_default";

    try {
      await getMessaging().send({
        token: fcmToken,
        notification: {
          title: data.title || "Heftreng",
          body:  data.sub   || data.message || "",
        },
        android: {
          priority: "high",
          notification: { channelId, icon: "ic_notif", color: "#8B5CF6" },
        },
        data: {
          type:    data.type    || "default",
          postId:  data.feedId  || data.postId || "",
          fromUid: data.fromUid || "",
          convId:  data.convId  || "",
        },
      });
      console.log("[HF Notif] ✓ push →", uid);
    } catch (err) {
      console.error("[HF Notif] FCM hatası:", err.code, err.message);
      if (err.code === "messaging/registration-token-not-registered") {
        await db.collection("users").doc(uid)
          .update({ fcmToken: null }).catch(() => {});
      }
    }
  }
);
