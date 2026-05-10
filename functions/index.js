/**
 * Heftreng — Cloud Functions
 * Sadece HTTPS Callable — Firestore trigger YOK
 * firebase-admin: ^12.0.0
 * firebase-functions: ^5.0.0
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp }      = require("firebase-admin/app");
const { getFirestore }       = require("firebase-admin/firestore");
const { getMessaging }       = require("firebase-admin/messaging");

initializeApp();

const STALE = [
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
];

// ── sendPush — Web tema + Android her ikisi de bunu çağırır ──────────────────
exports.sendPush = onCall(
  { region: "europe-west1", enforceAppCheck: false },
  async (request) => {
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
      throw new HttpsError("internal", "Firestore hatası: " + e.message);
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
        data: {
          type    : String(type    || "default"),
          postId  : String(postId  || ""),
          fromUid : String(fromUid || ""),
          convId  : String(convId  || ""),
        },
      });
      console.log("[HF Push] ✓", result, "→", targetUid);
      return { success: true };
    } catch (err) {
      console.error("[HF Push] FCM hatası:", err.code, err.message);
      if (STALE.includes(err.code)) {
        await db.collection("users").doc(targetUid)
          .update({ fcmToken: null }).catch(() => {});
        return { success: false, reason: "stale_token" };
      }
      throw new HttpsError("internal", err.message);
    }
  }
);
