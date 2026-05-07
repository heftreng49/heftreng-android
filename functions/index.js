/**
 * Heftreng — Cloud Functions
 * Deploy: firebase deploy --only functions
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp }     = require("firebase-admin/app");
const { getFirestore }      = require("firebase-admin/firestore");
const { getMessaging }      = require("firebase-admin/messaging");

initializeApp();

exports.sendPush = onCall(
  { region: "europe-west1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Giriş gerekli.");
    }

    const { targetUid, title, body, type, postId, fromUid, convId, url } = request.data;
    if (!targetUid) throw new HttpsError("invalid-argument", "targetUid gerekli.");

    const db = getFirestore();

    let fcmToken = null;
    try {
      const userDoc = await db.collection("users").doc(targetUid).get();
      if (!userDoc.exists) return { success: false, reason: "user_not_found" };
      fcmToken = userDoc.data().fcmToken || null;
    } catch (e) {
      console.error("[HF Push] Kullanıcı okunamadı:", e.message);
      throw new HttpsError("internal", "Kullanıcı okunamadı.");
    }

    if (!fcmToken) return { success: false, reason: "no_token" };

    const pushUrl = url || "https://heft-reng.blogspot.com/";

    // Bildirim tipine göre kanal seç
    const channelId = type === "message"
      ? "heftreng_messages"
      : (type === "like" || type === "repost")
        ? "heftreng_likes"
        : "heftreng_default";

    try {
      await getMessaging().send({
        token: fcmToken,

        // Başlık ve gövde — hem Android hem web için
        notification: {
          title: title || "Heftreng",
          body:  body  || "",
        },

        // ── Android native push ────────────────────────────────────────────
        android: {
          priority: "high",
          notification: {
            channelId: channelId,
            icon:      "ic_notif",
            color:     "#8B5CF6",
          },
          // HeftrangMessagingService.onMessageReceived tarafından okunur
          data: {
            type:    type    || "default",
            postId:  postId  || "",
            fromUid: fromUid || "",
            convId:  convId  || "",
          },
        },

        // ── Web push (Blogger PWA) ─────────────────────────────────────────
        webpush: {
          notification: {
            icon:         "https://heft-reng.blogspot.com/favicon.ico",
            badge:        "https://heft-reng.blogspot.com/favicon.ico",
            click_action: pushUrl,
          },
          fcmOptions: { link: pushUrl },
        },
      });

      console.log(`[HF Push] ✓ Gönderildi → uid:${targetUid} type:${type}`);
      return { success: true };

    } catch (err) {
      const STALE = [
        "messaging/registration-token-not-registered",
        "messaging/invalid-registration-token",
        "messaging/invalid-argument",
      ];
      if (STALE.includes(err.code)) {
        console.warn(`[HF Push] Eski token temizleniyor → uid:${targetUid}`);
        try { await db.collection("users").doc(targetUid).update({ fcmToken: null }); } catch(_) {}
        return { success: false, reason: "stale_token" };
      }
      console.error("[HF Push] FCM hatası:", err.message);
      throw new HttpsError("internal", "FCM gönderilemedi.");
    }
  }
);
