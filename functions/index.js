const functions = require("firebase-functions");
const admin     = require("firebase-admin");

admin.initializeApp();

exports.sendPush = functions
  .region("europe-west1")
  .https.onCall(async (data, context) => {

    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Giriş gerekli.");
    }

    const { targetUid, title, body, type, postId, fromUid, convId } = data;
    console.log("[HF Push] Çağrıldı → uid:", context.auth.uid, "target:", targetUid, "type:", type);

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
      console.warn("[HF Push] Token yok, uid:", targetUid);
      return { success: false, reason: "no_token" };
    }

    const channelId = type === "message"  ? "heftreng_messages"
      : (type === "like" || type === "repost") ? "heftreng_likes"
      : "heftreng_default";

    try {
      const result = await admin.messaging().send({
        token: fcmToken,
        notification: {
          title: title || "Heftreng",
          body:  body  || "",
        },
        android: {
          priority: "high",
          notification: {
            channelId: channelId,
            icon:      "ic_notif",
            color:     "#8B5CF6",
          },
          data: {
            type:    type    || "default",
            postId:  postId  || "",
            fromUid: fromUid || "",
            convId:  convId  || "",
          },
        },
      });
      console.log("[HF Push] ✓ Gönderildi:", result, "→ uid:", targetUid);
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
