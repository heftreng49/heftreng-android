
    /**
 * Heftreng — Cloud Functions
 * Deploy: firebase deploy --only functions
 *
 * v2 — onNewNotif Firestore trigger eklendi
 * Web → Android push artık SW/CORS/izin bağımsız çalışır:
 *   Web tema → userNotifs/{uid}/msgs koleksiyonuna yazar
 *   onNewNotif → otomatik tetiklenir → FCM push gönderir → Android bildirim alır
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated }  = require("firebase-functions/v2/firestore"); // ← YENİ
const { initializeApp }      = require("firebase-admin/app");
const { getFirestore }       = require("firebase-admin/firestore");
const { getMessaging }       = require("firebase-admin/messaging");

initializeApp();

// ─────────────────────────────────────────────────────────────────────────────
// onNewNotif — Firestore Trigger  ← YENİ EKLENEN FONKSİYON
//
// Ne zaman çalışır:
//   Web temada beğeni / yorum / takip / mesaj olunca
//   addNotifToUser() → userNotifs/{uid}/msgs koleksiyonuna belge eklenir
//   Bu fonksiyon otomatik tetiklenir ve FCM push gönderir
//
// Avantaj: Service Worker, CORS, web push izni GEREKMİYOR
//          Android kullanıcı arka planda bile bildirimi alır
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

    // Tip bazlı URL
    let url = "https://heft-reng.blogspot.com/";
    if (type === "message") {
      url = "https://heft-reng.blogspot.com/p/mesajlar.html";
    } else if (postId) {
      url = "https://heft-reng.blogspot.com/p/akis_01024829108.html";
    }

    const db = getFirestore();

    // Kullanıcının FCM token'ını al
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

    // Token yoksa veya web push subscription ise atla
    if (!fcmToken || fcmToken.startsWith("https://")) {
      console.log("[HF Trigger] FCM token yok, push atlandı — uid:", uid);
      return;
    }

    // Kanal ID — bildirim tipine göre
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
      data: {
        type,
        postId,
        fromUid,
        convId,
        url,
      },
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

// ─── sendPush — HTTPS Callable (mevcut, değiştirilmedi) ──────────────────────
// Android: FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("sendPush")
// Web:     firebase.functions().httpsCallable("sendPush")
// NOT: Web tema artık bunu çağırmak zorunda değil (onNewNotif üstlendi),
//      ama admin paneli ve Android için korunuyor.
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
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    // FCM mesajı gönder
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
        data: {
          type,
          postId,
          fromUid,
          convId,
          url,
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
