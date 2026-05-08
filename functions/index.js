/**
 * Heftreng — Cloud Functions
 * Deploy: firebase deploy --only functions
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp }      = require("firebase-admin/app");
const { getFirestore }       = require("firebase-admin/firestore");
const { getMessaging }       = require("firebase-admin/messaging");

initializeApp();

// ─── sendPush — FCM native push + web push ────────────────────────────────
// Android: FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("sendPush")
// Web:     firebase.functions().httpsCallable("sendPush")
exports.sendPush = onCall(
  {
    region: "europe-west1",
    // CORS — web tema için
    cors: true,
    // Kimlik doğrulama zorunlu değil (web tema login olmadan test edebilir)
    enforceAppCheck: false,
  },
  async (request) => {
    const callerUid = request.auth?.uid || "anonymous";
    console.log("[HF Push] Çağrıldı — caller:", callerUid);

    const {
      targetUid,
      title  = "Heftreng",
      body   = "",
      type   = "default",
      postId = "",
      fromUid= "",
      convId = "",
      url    = "https://heft-reng.blogspot.com/",
    } = request.data || {};

    if (!targetUid) {
      console.warn("[HF Push] targetUid eksik");
      return { success: false, reason: "no_target" };
    }

    const db = getFirestore();

    // Kullanıcı belgesini oku
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

    console.log("[HF Push] fcmToken:", fcmToken ? "VAR" : "YOK", "— target:", targetUid);

    if (!fcmToken) {
      console.warn("[HF Push] FCM token yok — uid:", targetUid);
      return { success: false, reason: "no_fcm_token" };
    }

    // Web push subscription token ise FCM ile gönderme
    if (fcmToken.startsWith("https://")) {
      console.warn("[HF Push] Web push subscription — FCM desteklenmiyor");
      return { success: false, reason: "web_sub_not_supported" };
    }

    // Kanal ID — bildirim tipine göre
    const channelId =
      type === "message" ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes" :
      "heftreng_default";

    // FCM mesajı gönder
    try {
      const msg = {
        token: fcmToken,
        notification: {
          title: title,
          body : body,
        },
        android: {
          priority: "high",
          notification: {
            channelId,
            icon : "ic_notif",
            color: "#8B5CF6",
          },
        },
        // Data payload — Android'de deep link için
        data: {
          type   : type,
          postId : postId,
          fromUid: fromUid,
          convId : convId,
          url    : url,
        },
      };

      const result = await getMessaging().send(msg);
      console.log("[HF Push] ✓ FCM gönderildi — messageId:", result);
      return { success: true, messageId: result };

    } catch (err) {
      console.error("[HF Push] FCM hatası:", err.code, err.message);

      // Eski/geçersiz token — sil
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
