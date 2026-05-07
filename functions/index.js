/**
 * Heftreng — Cloud Functions
 * Tema tarafından httpsCallable ile çağrılır.
 *
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
    /* Sadece giriş yapmış kullanıcılar çağırabilir */
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Giriş gerekli.");
    }

    const { targetUid, title, body, url } = request.data;
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

    try {
      await getMessaging().send({
        token: fcmToken,
        notification: { title: title || "Heftreng", body: body || "" },
        webpush: {
          notification: {
            icon:         "https://heft-reng.blogspot.com/favicon.ico",
            badge:        "https://heft-reng.blogspot.com/favicon.ico",
            click_action: pushUrl,
          },
          fcmOptions: { link: pushUrl },
        },
      });
      console.log(`[HF Push] ✓ Gönderildi → uid:${targetUid}`);
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
