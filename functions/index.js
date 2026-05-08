/**
 * Heftreng — Cloud Functions
 * Deploy: firebase deploy --only functions
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp }     = require("firebase-admin/app");
const { getFirestore }      = require("firebase-admin/firestore");
const { getMessaging }      = require("firebase-admin/messaging");
const webpush               = require("web-push");

initializeApp();

// VAPID keys — Firebase Console > Project Settings > Cloud Messaging
const VAPID_PUBLIC  = "S9MRCYrsC6b2y4VxRC_SPFM8FDFCxAYn5yGkWFo8p1o";
const VAPID_PRIVATE = process.env.VAPID_PRIVATE_KEY || "";
const VAPID_SUBJECT = "mailto:heftreng49@gmail.com";

if (VAPID_PRIVATE) {
  webpush.setVapidDetails(VAPID_SUBJECT, VAPID_PUBLIC, VAPID_PRIVATE);
}

exports.sendPush = onCall(
  { region: "europe-west1" },
  async (request) => {
    console.log("[HF Push] Çağrıldı, uid:", request.auth?.uid || "YOK");

    if (!request.auth) throw new HttpsError("unauthenticated", "Giriş gerekli.");

    const { targetUid, title, body, type, postId, fromUid, convId, url } = request.data;
    console.log("[HF Push] target:", targetUid, "type:", type);
    if (!targetUid) throw new HttpsError("invalid-argument", "targetUid gerekli.");

    const db = getFirestore();
    let userData = null;

    try {
      const doc = await db.collection("users").doc(targetUid).get();
      if (!doc.exists) { console.warn("[HF Push] Kullanıcı yok:", targetUid); return { success: false, reason: "user_not_found" }; }
      userData = doc.data();
    } catch (e) {
      console.error("[HF Push] Firestore hatası:", e.message);
      throw new HttpsError("internal", "Kullanıcı okunamadı.");
    }

    const fcmToken   = userData.fcmToken   || null;
    const webPushSub = userData.webPushSub || null;
    const pushUrl    = url || "https://heft-reng.blogspot.com/";

    console.log("[HF Push] fcmToken:", fcmToken ? "VAR" : "YOK", "webPushSub:", webPushSub ? "VAR" : "YOK");

    if (!fcmToken && !webPushSub) {
      console.warn("[HF Push] Token yok, uid:", targetUid);
      return { success: false, reason: "no_token" };
    }

    const channelId = type === "message" ? "heftreng_messages"
      : (type === "like" || type === "repost") ? "heftreng_likes"
      : "heftreng_default";

    // ── 1. FCM ile Android native push ──────────────────────────────────────
    if (fcmToken && !fcmToken.startsWith("https://")) {
      try {
        const result = await getMessaging().send({
          token: fcmToken,
          notification: { title: title || "Heftreng", body: body || "" },
          android: {
            priority: "high",
            notification: { channelId, icon: "ic_notif", color: "#8B5CF6" },
            data: { type: type||"default", postId: postId||"", fromUid: fromUid||"", convId: convId||"" },
          },
          webpush: {
            notification: { icon: "https://heft-reng.blogspot.com/favicon.ico", click_action: pushUrl },
            fcmOptions: { link: pushUrl },
          },
        });
        console.log("[HF Push] ✓ FCM gönderildi:", result);
        return { success: true };
      } catch (err) {
        console.error("[HF Push] FCM hatası:", err.code, err.message);
        const STALE = ["messaging/registration-token-not-registered","messaging/invalid-registration-token","messaging/invalid-argument"];
        if (STALE.includes(err.code)) {
          await db.collection("users").doc(targetUid).update({ fcmToken: null }).catch(() => {});
          // webPushSub varsa onunla devam et
          if (!webPushSub) return { success: false, reason: "stale_token" };
        } else {
          throw new HttpsError("internal", err.message);
        }
      }
    }

    // ── 2. Web Push API — Blogger PWA için ──────────────────────────────────
    if (webPushSub) {
      try {
        const sub = JSON.parse(typeof webPushSub === "string" ? webPushSub : JSON.stringify(webPushSub));
        const payload = JSON.stringify({
          title: title || "Heftreng",
          body:  body  || "",
          icon:  "https://raw.githubusercontent.com/heftreng49/depo/master/icons/icon-192.png",
          url:   pushUrl,
          type:  type || "default",
        });
        await webpush.sendNotification(sub, payload);
        console.log("[HF Push] ✓ Web Push gönderildi, uid:", targetUid);
        return { success: true };
      } catch (err) {
        console.error("[HF Push] Web Push hatası:", err.statusCode, err.message);
        if (err.statusCode === 410 || err.statusCode === 404) {
          await db.collection("users").doc(targetUid).update({ webPushSub: null }).catch(() => {});
          return { success: false, reason: "stale_subscription" };
        }
        throw new HttpsError("internal", "Web Push gönderilemedi.");
      }
    }

    return { success: false, reason: "no_token" };
  }
);
