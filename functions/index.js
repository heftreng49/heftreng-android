/**
 * Heftreng — Cloud Functions
 * firebase-functions v5 / v2 uyumlu optimized sürüm
 */

const { onCall, HttpsError, onRequest } = require("firebase-functions/v2/https");
const { onDocumentCreated }             = require("firebase-functions/v2/firestore");
const functions                         = require("firebase-functions");
const { initializeApp }                 = require("firebase-admin/app");
const { getFirestore }                  = require("firebase-admin/firestore");
const { getMessaging }                  = require("firebase-admin/messaging");

initializeApp();

// ─── Yardımcı: Auth verisinden güvenli displayName üret ─────────────────────
function deriveName(authUser) {
  return (authUser.displayName || "").trim()
      || (authUser.email || "").split("@")[0].replace(/[._-]/g, " ").trim()
      || "Kullanıcı";
}

// ─── onNewNotif — Firestore Trigger (v2) ────────────────────────────────────
exports.onNewNotif = onDocumentCreated(
  { document: "userNotifs/{uid}/msgs/{msgId}", region: "europe-west1" },
  async (event) => {
    const uid  = event.params.uid;
    const data = event.data?.data();
    if (!data) { console.warn("[HF Trigger] Belge boş — uid:", uid); return; }

    const type    = data.type    || "default";
    const title   = data.title   || "Heftreng";
    const body    = data.sub     || data.message || "";
    const postId  = data.feedId  || data.postId  || "";
    const fromUid = data.fromUid || "";
    const convId  = data.convId  || "";

    let url = "https://heft-reng.blogspot.com/";
    if (type === "message") url = "https://heft-reng.blogspot.com/p/mesajlar.html";
    else if (postId)        url = "https://heft-reng.blogspot.com/p/akis_01024829108.html";

    const db = getFirestore();
    let fcmToken = null;
    try {
      const doc = await db.collection("users").doc(uid).get();
      if (!doc.exists) { console.log("[HF Trigger] Kullanıcı yok — uid:", uid); return; }
      fcmToken = doc.data()?.fcmToken || null;
    } catch (e) { console.error("[HF Trigger] Firestore hatası:", e.message); return; }

    if (!fcmToken || fcmToken.startsWith("https://")) {
      console.log("[HF Trigger] FCM token yok — uid:", uid); return;
    }

    const channelId =
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    const msg = {
      token: fcmToken,
      android: { priority: "high" },
      data: { type, postId, fromUid, convId, url, title, body, channelId },
    };
    const STALE = [
      "messaging/registration-token-not-registered",
      "messaging/invalid-registration-token",
      "messaging/invalid-argument",
    ];
    try {
      const result = await getMessaging().send(msg);
      console.log("[HF Trigger] ✓ FCM →", uid, result);
    } catch (err) {
      console.error("[HF Trigger] FCM hatası:", err.code, "→ uid:", uid);
      if (STALE.includes(err.code))
        await db.collection("users").doc(uid).update({ fcmToken: null }).catch(() => {});
    }
  }
);

// ─── sendPush — HTTPS Callable (v2) ─────────────────────────────────────────
exports.sendPush = onCall(
  { region: "europe-west1", cors: true, enforceAppCheck: false },
  async (request) => {
    const { targetUid, title = "Heftreng", body = "", type = "default",
            postId = "", fromUid = "", convId = "",
            url = "https://heft-reng.blogspot.com/" } = request.data || {};

    if (!targetUid) return { success: false, reason: "no_target" };

    const db = getFirestore();
    let userData;
    try {
      const doc = await db.collection("users").doc(targetUid).get();
      if (!doc.exists) return { success: false, reason: "user_not_found" };
      userData = doc.data();
    } catch (e) { throw new HttpsError("internal", "Kullanıcı okunamadı."); }

    const fcmToken = userData.fcmToken || null;
    if (!fcmToken) return { success: false, reason: "no_fcm_token" };
    if (fcmToken.startsWith("https://")) return { success: false, reason: "web_sub_not_supported" };

    const channelId =
      type === "message"                   ? "heftreng_messages" :
      type === "like" || type === "repost" ? "heftreng_likes"    :
      "heftreng_default";

    try {
      const msg = {
        token: fcmToken,
        android: { priority: "high" },
        data: { type, postId, fromUid, convId, url, title, body, channelId },
      };
      const result = await getMessaging().send(msg);
      return { success: true, messageId: result };
    } catch (err) {
      const staleErrors = [
        "messaging/registration-token-not-registered",
        "messaging/invalid-registration-token",
        "messaging/invalid-argument",
      ];
      if (staleErrors.includes(err.code)) {
        await db.collection("users").doc(targetUid).update({ fcmToken: null }).catch(() => {});
        return { success: false, reason: "stale_token" };
      }
      throw new HttpsError("internal", err.message);
    }
  }
);

// ─── fixNewUserDisplayName — Auth onCreate (v1 gen1) ────────────────────────
exports.fixNewUserDisplayName = functions
  .runWith({})
  .region("us-central1")
  .auth.user()
  .onCreate(async (user) => {
    // Bot Koruması: Eğer e-posta doğrulaması açıksa ve bot sahte maillerle gelmişse Firestore kaydı yapma
    // İhtiyacına göre aktif edebilirsin: if (!user.emailVerified) return;

    const db = getFirestore();
    try {
      const derived = deriveName(user);
      const base    = (user.email || "").split("@")[0]
                        .toLowerCase().replace(/[^a-z0-9]/g, "");

      await db.collection("users").doc(user.uid).set({
        uid        : user.uid,
        displayName: derived,
        name       : derived,
        username   : base || user.uid.slice(0, 8),
        email      : user.email    || "",
        photoURL   : user.photoURL || "",
        createdAt  : new Date(),
        followers  : 0,
        following  : 0,
        postCount  : 0,
      }, { merge: true });

      console.log(`[fixNewUser] ✓ ${user.uid} → "${derived}"`);
    } catch (e) {
      console.error("[fixNewUser] hata:", e);
    }
  });

// ─── repairAllUsers — HTTP endpoint ─────────────────────────────────────────
exports.repairAllUsers = onRequest(async (req, res) => {
  if (req.query.secret !== "hf2024") {
    res.status(403).json({ error: "Yetkisiz" }); return;
  }

  const db   = getFirestore();
  const auth = require("firebase-admin/auth").getAuth();

  let created = 0, patched = 0, skipped = 0, errors = 0;
  const details = [];

  try {
    let pageToken;
    do {
      const listResult = await auth.listUsers(1000, pageToken);
      pageToken = listResult.pageToken;

      for (const authUser of listResult.users) {
        try {
          const ref  = db.collection("users").doc(authUser.uid);
          const snap = await ref.get();
          const derived = deriveName(authUser);
          const base    = (authUser.email || "").split("@")[0]
                            .toLowerCase().replace(/[^a-z0-9]/g, "");

          if (!snap.exists) {
            await ref.set({
              uid        : authUser.uid,
              displayName: derived,
              name       : derived,
              username   : base || authUser.uid.slice(0, 8),
              email      : authUser.email    || "",
              photoURL   : authUser.photoURL || "",
              createdAt  : new Date(),
              followers  : 0,
              following  : 0,
              postCount  : 0,
            });
            created++;
            details.push({ uid: authUser.uid, status: "created", name: derived });

          } else {
            const data  = snap.data();
            const patch = {};

            if (!data.displayName || data.displayName === "Kullanıcı") patch.displayName = derived;
            if (!data.name || data.name === "Kullanıcı") patch.name = derived;
            if (!data.photoURL && authUser.photoURL) patch.photoURL = authUser.photoURL;
            if (!data.email && authUser.email) patch.email = authUser.email;
            if (!data.uid) patch.uid = authUser.uid;
            if (!data.username) patch.username = base || authUser.uid.slice(0, 8);
            if (data.followers  === undefined) patch.followers  = 0;
            if (data.following  === undefined) patch.following  = 0;
            if (data.postCount  === undefined) patch.postCount  = 0;
            if (!data.createdAt) patch.createdAt = new Date();

            if (Object.keys(patch).length > 0) {
              await ref.update(patch);
              patched++;
              details.push({ uid: authUser.uid, status: "patched", fields: Object.keys(patch) });
            } else {
              skipped++;
            }
          }
        } catch (e) {
          errors++;
          details.push({ uid: authUser.uid, status: "error", error: e.message });
        }
      }
    } while (pageToken);

    res.json({ created, patched, skipped, errors, details });

  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ─────────────────────────────────────────────────────────────────────────────
//  refreshStats — OPTIMIZED (Blaze Dostu, count() kullanan sürüm)
// ─────────────────────────────────────────────────────────────────────────────
exports.refreshStats = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Giriş gerekli");

  const db    = getFirestore(); // Hata düzeltildi: admin yerine v2 referansı
  const now   = Date.now();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const todaySec  = Math.floor(today.getTime() / 1000);
  const twoMinAgo = Math.floor(now / 1000) - 120;

  try {
    // Tüm verileri .get() ile indirmek yerine .count() ile bulutta saydırıyoruz (Maliyet/Hafıza dostu)
    const [
      totalUsersCount, androidUsersCount, webUsersCount, bannedUsersCount,
      totalPostsCount, pendingPostsCount,
      totalQuotesCount, totalReviewsCount, totalSerialsCount, totalBooksCount,
      pendingReportsCount, onlineNowCount
    ] = await Promise.all([
      db.collection("users").count().get(),
      db.collection("users").where("platform", "==", "android").count().get(),
      db.collection("users").where("platform", "in", ["web", ""]).count().get(),
      db.collection("users").where("banned", "==", true).count().get(),
      db.collection("feed").where("moderationStatus", "==", "active").count().get(),
      db.collection("feed").where("moderationStatus", "==", "suspended").count().get(),
      db.collectionGroup("quotes").count().get(),
      db.collectionGroup("reviews").count().get(),
      db.collection("serials").count().get(),
      db.collection("library_books").count().get(),
      db.collection("reports").where("status", "==", "pending").count().get(),
      db.collection("presence").where("online", "==", true).where("lastSeen", ">=", new Date(twoMinAgo * 1000)).count().get()
    ]);

    // Bugün katılanlar ve bugün atılan postlar zaman filtresi gerektirdiği için sayfa bazlı kontrol edilebilir
    // Ancak pratiklik açısından şimdilik basit bir get yerine tarih filtreli count yapıyoruz:
    const newUsersTodayCount = await db.collection("users").where("createdAt", ">=", new Date(todaySec * 1000)).count().get();
    const newPostsTodayCount = await db.collection("feed").where("ts", ">=", new Date(todaySec * 1000)).where("moderationStatus", "==", "active").count().get();

    const stats = {
      totalUsers: totalUsersCount.data().count,
      androidUsers: androidUsersCount.data().count,
      webUsers: webUsersCount.data().count,
      onlineNow: onlineNowCount.data().count,
      newUsersToday: newUsersTodayCount.data().count,
      totalPosts: totalPostsCount.data().count,
      newPostsToday: newPostsTodayCount.data().count,
      pendingPosts: pendingPostsCount.data().count,
      totalQuotes: totalQuotesCount.data().count,
      totalReviews: totalReviewsCount.data().count,
      totalSerials: totalSerialsCount.data().count,
      totalBooks: totalBooksCount.data().count,
      pendingReports: pendingReportsCount.data().count,
      bannedUsers: bannedUsersCount.data().count,
      totalComments: 0,
      lastUpdated: now,
    };

    await db.collection("appConfig").doc("stats").set(stats);
    console.log("[refreshStats] Başarıyla güncellendi");
    return { success: true };

  } catch (err) {
    console.error("[refreshStats] Hata:", err.message);
    throw new functions.https.HttpsError("internal", err.message);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
//  scheduledRefreshStats — OPTIMIZED (Her gece çalışan otomatik sürüm)
// ─────────────────────────────────────────────────────────────────────────────
exports.scheduledRefreshStats = functions.pubsub
  .schedule("0 2 * * *")
  .timeZone("Europe/Istanbul")
  .onRun(async () => {
    const db    = getFirestore();
    const now   = Date.now();
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const todaySec  = Math.floor(today.getTime() / 1000);
    const twoMinAgo = Math.floor(now / 1000) - 120;

    try {
      const [
        totalUsersCount, androidUsersCount, webUsersCount, bannedUsersCount,
        totalPostsCount, pendingPostsCount,
        totalQuotesCount, totalReviewsCount, totalSerialsCount, totalBooksCount,
        pendingReportsCount, onlineNowCount, newUsersTodayCount, newPostsTodayCount
      ] = await Promise.all([
        db.collection("users").count().get(),
        db.collection("users").where("platform", "==", "android").count().get(),
        db.collection("users").where("platform", "in", ["web", ""]).count().get(),
        db.collection("users").where("banned", "==", true).count().get(),
        db.collection("feed").where("moderationStatus", "==", "active").count().get(),
        db.collection("feed").where("moderationStatus", "==", "suspended").count().get(),
        db.collectionGroup("quotes").count().get(),
        db.collectionGroup("reviews").count().get(),
        db.collection("serials").count().get(),
        db.collection("library_books").count().get(),
        db.collection("reports").where("status", "==", "pending").count().get(),
        db.collection("presence").where("online", "==", true).where("lastSeen", ">=", new Date(twoMinAgo * 1000)).count().get(),
        db.collection("users").where("createdAt", ">=", new Date(todaySec * 1000)).count().get(),
        db.collection("feed").where("ts", ">=", new Date(todaySec * 1000)).where("moderationStatus", "==", "active").count().get()
      ]);

      await db.collection("appConfig").doc("stats").set({
        totalUsers: totalUsersCount.data().count,
        androidUsers: androidUsersCount.data().count,
        webUsers: webUsersCount.data().count,
        bannedUsers: bannedUsersCount.data().count,
        newUsersToday: newUsersTodayCount.data().count,
        onlineNow: onlineNowCount.data().count,
        totalPosts: totalPostsCount.data().count,
        newPostsToday: newPostsTodayCount.data().count,
        pendingPosts: pendingPostsCount.data().count,
        totalQuotes: totalQuotesCount.data().count,
        totalReviews: totalReviewsCount.data().count,
        totalSerials: totalSerialsCount.data().count,
        totalBooks: totalBooksCount.data().count,
        pendingReports: pendingReportsCount.data().count,
        totalComments: 0,
        lastUpdated: now,
      });

      console.log("[scheduledRefreshStats] Zamanlanmış görev başarıyla tamamlandı.");
    } catch (e) {
      console.error("[scheduledRefreshStats] Hata oluştu:", e.message);
    }
    return null;
  });
