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

// ─── Admin secret — env variable olarak saklanır ─────────────────────────────
// Firebase Console → Functions → Config ya da Secret Manager'dan okunur.
// Fallback: hf2024 (eski default — deploy'dan sonra env'e geçin)
const ADMIN_SECRET = process.env.HEFTRENG_ADMIN_SECRET || "hf2024";

// ─── Yardımcı: request IP bazlı in-memory rate limiter ──────────────────────
// Cloud Functions her instance'ı warm-up'ta sıfırlar, production için
// Firestore/Redis tabanlı rate limiting önerilir; bu hafif bir ilk korumadır.
const _ipHits = new Map(); // ip → { count, windowStart }
function checkIpRateLimit(ip, maxPerMinute = 20) {
  const now = Date.now();
  const hit = _ipHits.get(ip) || { count: 0, windowStart: now };
  if (now - hit.windowStart > 60_000) {
    _ipHits.set(ip, { count: 1, windowStart: now });
    return true;
  }
  hit.count++;
  _ipHits.set(ip, hit);
  return hit.count <= maxPerMinute;
}

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
  { region: "europe-west1", cors: true },   // enforceAppCheck kaldırıldı (default: false; App Check aktifse true yapın)
  async (request) => {
    // 1. Kimlik doğrulama zorunlu
    if (!request.auth) throw new HttpsError("unauthenticated", "Giriş gerekli.");

    // 2. Email doğrulanmış veya Google ile giriş yapılmış olmalı
    const token = request.auth.token;
    const isVerified = token.email_verified === true
        || token.firebase?.sign_in_provider === "google.com"
        || token.firebase?.sign_in_provider === "apple.com"
        || token.email?.endsWith("@gmail.com"); // Google hesapları her zaman doğrulanmış
    if (!isVerified) {
      console.warn("[sendPush] Doğrulanmamış kullanıcı:", request.auth.uid);
      throw new HttpsError("permission-denied", "Email doğrulanmamış.");
    }

    const { targetUid, title = "Heftreng", body = "", type = "default",
            postId = "", fromUid = "", convId = "",
            url = "https://heft-reng.blogspot.com/" } = request.data || {};

    if (!targetUid) return { success: false, reason: "no_target" };

    // 3. Kendine push göndermeyi engelle (spam önlemi)
    if (targetUid === request.auth.uid) return { success: false, reason: "self_push" };

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
const DISPOSABLE_DOMAINS = [
  "mailinator.com","guerrillamail.com","temp-mail.org","throwam.com",
  "yopmail.com","sharklasers.com","guerrillamailblock.com","grr.la",
  "guerrillamail.info","spam4.me","trashmail.com","trashmail.net",
  "fakeinbox.com","mailnull.com","spamgourmet.com","10minutemail.com",
  "tempmail.com","dispostable.com","mailnesia.com","maildrop.cc",
  "getairmail.com","filzmail.com","throwam.com","discard.email",
  "spamboy.com","spamherelots.com","tempr.email","tempm.com",
];

function isDisposableEmail(email) {
  if (!email) return false;
  const domain = email.split("@")[1]?.toLowerCase() || "";
  return DISPOSABLE_DOMAINS.includes(domain);
}

function isSuspiciousDisplayName(name) {
  if (!name) return false;
  const hasNoVowelRatio = (name.replace(/[^a-zA-Z]/g, "").match(/[aeiouAEIOU]/g) || []).length /
                          Math.max(name.replace(/[^a-zA-Z]/g, "").length, 1) < 0.1;
  const isTooLong = name.length > 25 && !name.includes(" ");
  return hasNoVowelRatio || isTooLong;
}

exports.fixNewUserDisplayName = functions
  .runWith({})
  .region("us-central1")
  .auth.user()
  .onCreate(async (user) => {
    const db  = getFirestore();
    const adm = require("firebase-admin/auth").getAuth();

    if (user.email && isDisposableEmail(user.email)) {
      console.warn(`[botBlock] Disposable email engellendi: ${user.email} (${user.uid})`);
      await db.collection("blockedSignups").doc(user.uid).set({
        uid: user.uid, email: user.email, reason: "disposable_email",
        blockedAt: new Date(),
      });
      try { await adm.deleteUser(user.uid); } catch (_) {}
      return;
    }

    const derived = deriveName(user);
    if (!user.providerData?.some(p => p.providerId === "google.com") &&
        isSuspiciousDisplayName(derived)) {
      console.warn(`[botBlock] Şüpheli isim: "${derived}" (${user.uid})`);
      await db.collection("users").doc(user.uid).set({
        uid: user.uid, banned: true, moderationStatus: "suspended",
        moderationNote: "Otomatik: şüpheli kayıt", createdAt: new Date(),
      }, { merge: true });
    }

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

// ─── repairAllUsers — HTTPS v2 Endpoint (europe-west1) ──────────────────────
exports.repairAllUsers = onRequest(
  { region: "europe-west1", cors: true },
  async (req, res) => {
    if (req.query.secret !== ADMIN_SECRET) {
      res.status(403).json({ error: "Yetkisiz" }); return;
    }
    const ip = req.headers["x-forwarded-for"] || req.ip || "unknown";
    if (!checkIpRateLimit(ip, 5)) {
      res.status(429).json({ error: "Rate limit aşıldı." }); return;
    }
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
  }
);

// ─── refreshStats — OPTIMIZED (Blaze Dostu, count() kullanan sürüm) ──────────
exports.refreshStats = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Giriş gerekli");

  const db   = getFirestore();
  // Admin veya editor rolü gerekli
  const adminDoc = await db.collection("admins").doc(context.auth.uid).get().catch(() => null);
  const role = adminDoc?.exists ? adminDoc.data()?.role : null;
  if (!["admin", "editor"].includes(role))
    throw new functions.https.HttpsError("permission-denied", "Yetkisiz.");

  const now   = Date.now();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const todaySec  = Math.floor(today.getTime() / 1000);
  const twoMinAgo = Math.floor(now / 1000) - 120;

  try {
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

// ─── scheduledRefreshStats — OPTIMIZED (Her gece otomatik sürüm) ─────────────
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

// ─── verifyRegistration — Kayıt öncesi güvenlik kontrolü ────────────────────
exports.verifyRegistration = functions.https.onCall(async (data, context) => {
  // Kayıt sırasında çağrılır — kullanıcı henüz oturum açmamış olabilir.
  // Ama IP bazlı rate limiting uygulanır.
  const email       = (data.email || "").toLowerCase().trim();
  const displayName = (data.displayName || "").trim();
  const db          = getFirestore();

  if (isDisposableEmail(email)) {
    return { allowed: false, reason: "Bu email adresi geçici/sahte email servisi. Gerçek bir email kullanın." };
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  if (!emailRegex.test(email)) {
    return { allowed: false, reason: "Geçersiz email formatı." };
  }

  if (!displayName || displayName.length < 2) {
    return { allowed: false, reason: "İsim en az 2 karakter olmalı." };
  }
  if (displayName.length > 40) {
    return { allowed: false, reason: "İsim çok uzun." };
  }

  const domain     = email.split("@")[1] || "";
  const tenMinAgo  = new Date(Date.now() - 10 * 60 * 1000);
  try {
    const recentSnap = await db.collection("signupAttempts")
      .where("domain", "==", domain)
      .where("at", ">", tenMinAgo)
      .get();
    if (recentSnap.size >= 5) {
      console.warn(`[verifyReg] Rate limit: ${domain} → ${recentSnap.size} kayıt/10dk`);
      return { allowed: false, reason: "Çok fazla kayıt denemesi. Lütfen biraz bekleyin." };
    }
  } catch (_) {}

  await db.collection("signupAttempts").add({
    email, domain, displayName, at: new Date(),
  });

  return { allowed: true };
});

// ─── cleanBlockedSignups — Eski kayıtları haftalık temizle ────────────────────
exports.cleanBlockedSignups = functions.pubsub
  .schedule("0 3 * * 0")
  .timeZone("Europe/Istanbul")
  .onRun(async () => {
    const db      = getFirestore();
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const snap    = await db.collection("blockedSignups").where("blockedAt", "<", weekAgo).get();
    const batch   = db.batch();
    snap.docs.forEach(d => batch.delete(d.ref));
    await batch.commit();

    const dayAgo  = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const snap2   = await db.collection("signupAttempts").where("at", "<", dayAgo).get();
    const batch2  = db.batch();
    snap2.docs.forEach(d => batch2.delete(d.ref));
    await batch2.commit();

    console.log(`[cleanup] ${snap.size} blocked + ${snap2.size} attempts silindi`);
    return null;
  });

// ─── repairAuthorQuotes — Eski yazar alıntılarını düzelt (v2 ONREQUEST) ──────
// Kullanım: GET https://europe-west1-bloggerheftreng.cloudfunctions.net/repairAuthorQuotes?secret=hf2024
exports.repairAuthorQuotes = onRequest(
  { region: "europe-west1", cors: true }, 
  async (req, res) => {
    if (req.query.secret !== ADMIN_SECRET) {
      return res.status(403).send("Forbidden: Yetkisiz Erişim");
    }
    const ip2 = req.headers["x-forwarded-for"] || req.ip || "unknown";
    if (!checkIpRateLimit(ip2, 5)) return res.status(429).send("Rate limit aşıldı.");
    let feedFixed = 0, subFixed = 0, booksFixed = 0, errors = 0;

    // ── 1. Feed postlarını düzelt ─────────────────────────────────────────────
    try {
      const feedSnap = await db.collection("feed")
        .where("type", "==", "library_quote")
        .limit(500).get();

      const bookCache = {};
      const getBook = async (bookId) => {
        if (!bookId) return null;
        if (bookCache[bookId]) return bookCache[bookId];
        const doc = await db.collection("library_books").doc(bookId).get();
        bookCache[bookId] = doc.exists ? doc.data() : null;
        return bookCache[bookId];
      };

      const authorCache = {};
      const getAuthorId = async (authorName) => {
        if (!authorName) return "";
        if (authorCache[authorName]) return authorCache[authorName];
        const snap = await db.collection("library_authors")
          .where("name", "==", authorName).limit(1).get();
        const id = snap.empty ? "" : snap.docs[0].id;
        authorCache[authorName] = id;
        return id;
      };

      const batch = db.batch();
      let batchCount = 0;

      for (const doc of feedSnap.docs) {
        const d = doc.data();
        const updates = {};

        if (!d.libraryAuthorId || d.libraryAuthorId === "") {
          let authorId = "";
          if (d.libraryBookId) {
            const book = await getBook(d.libraryBookId);
            if (book?.authorId) authorId = book.authorId;
          }
          if (!authorId && d.authorName) {
            authorId = await getAuthorId(d.authorName);
          }
          if (authorId) {
            updates["libraryAuthorId"] = authorId;
          }
        }

        if (!d.libraryBookId && d.bookName) {
          const bookSnap = await db.collection("library_books")
            .where("title", "==", d.bookName).limit(1).get();
          if (!bookSnap.empty) {
            const book = bookSnap.docs[0];
            updates["libraryBookId"] = book.id;
            if (!updates["libraryAuthorId"] && book.data().authorId) {
              updates["libraryAuthorId"] = book.data().authorId;
            }
          }
        }

        if (Object.keys(updates).length > 0) {
          batch.update(doc.ref, updates);
          feedFixed++;
          batchCount++;

          if (batchCount >= 490) {
            await batch.commit();
            batchCount = 0;
          }
        }
      }

      if (batchCount > 0) await batch.commit();
    } catch (e) {
      console.error("feed repair error:", e.message);
      errors++;
    }

    // ── 2. Subcollection quotes'ları düzelt ───────────────────────────────────
    try {
      const booksSnap = await db.collection("library_books").limit(200).get();

      for (const bookDoc of booksSnap.docs) {
        const book = bookDoc.data();
        if (!book.authorId) continue;

        const quotesSnap = await db.collection("library_books")
          .doc(bookDoc.id).collection("quotes")
          .where("authorId", "==", "").limit(50).get();

        if (quotesSnap.empty) continue;

        const batch = db.batch();
        quotesSnap.docs.forEach(qDoc => {
          batch.update(qDoc.ref, {
            "authorId"   : book.authorId,
            "authorName" : book.authorName || book.author || "",
          });
          subFixed++;
        });
        await batch.commit();
      }
    } catch (e) {
      console.error("subcollection repair error:", e.message);
      errors++;
    }

    // ── 3. library_books — authorId'leri düzelt ───────────────────────────────
    try {
      const booksSnap = await db.collection("library_books").limit(500).get();

      const authorNameCache = {};
      const findAuthorId = async (authorName) => {
        if (!authorName) return "";
        if (authorNameCache[authorName]) return authorNameCache[authorName];
        const snap = await db.collection("library_authors")
          .where("name", "==", authorName).limit(1).get();
        const id = snap.empty ? "" : snap.docs[0].id;
        authorNameCache[authorName] = id;
        return id;
      };

      const batch3 = db.batch();
      let b3count = 0;

      for (const bookDoc of booksSnap.docs) {
        const d = bookDoc.data();
        if (d.authorId && d.authorId !== "") continue;

        const authorName = d.authorName || d.author || "";
        const authorId   = await findAuthorId(authorName);
        if (!authorId) continue;

        batch3.update(bookDoc.ref, { "authorId": authorId });
        booksFixed++;
        b3count++;

        if (b3count >= 490) { await batch3.commit(); b3count = 0; }
      }
      if (b3count > 0) await batch3.commit();
    } catch (e) {
      console.error("library_books repair error:", e.message);
      errors++;
    }

    return res.json({
      ok: true,
      feedFixed,
      subFixed,
      booksFixed,
      errors,
      message: `${feedFixed} feed + ${subFixed} alıntı + ${booksFixed} kitap başarıyla düzeltildi.`,
    });
  }
);

// ─────────────────────────────────────────────────────────────────────────────
//  mergeLibraryBooks — Aynı başlık+yazar kombinasyonundaki duplicate kitapları birleştir
//  Kullanım: GET /mergeLibraryBooks?secret=hf2024
// ─────────────────────────────────────────────────────────────────────────────
exports.mergeLibraryBooks = onRequest({ region: "europe-west1" }, async (req, res) => {
  if (req.query.secret !== ADMIN_SECRET) return res.status(403).send("Forbidden");
  const _ipM = req.headers["x-forwarded-for"] || req.ip || "unknown";
  if (!checkIpRateLimit(_ipM, 5)) return res.status(429).send("Rate limit.");

  const db = getFirestore();
  let merged = 0, errors = 0;

  try {
    const snap = await db.collection("library_books").limit(500).get();

    // titleLower + authorId kombinasyonuna göre grupla
    const groups = {};
    snap.docs.forEach(doc => {
      const d = doc.data();
      const key = `${(d.titleLower || d.title || "").toLowerCase().trim()}__${d.authorId || ""}`;
      if (!groups[key]) groups[key] = [];
      groups[key].push({ id: doc.id, data: d, ref: doc.ref });
    });

    // Birden fazla kitap olan grupları işle
    for (const [key, books] of Object.entries(groups)) {
      if (books.length < 2) continue;

      // En fazla alıntı/inceleme olan kitabı "hayatta kalan" olarak seç
      books.sort((a, b) =>
        ((b.data.quoteCount || 0) + (b.data.reviewCount || 0)) -
        ((a.data.quoteCount || 0) + (a.data.reviewCount || 0))
      );
      const survivor  = books[0];
      const duplicates = books.slice(1);

      for (const dup of duplicates) {
        try {
          // 1. Feed postlarını güncelle — duplicate bookId → survivor bookId
          const feedSnap = await db.collection("feed")
            .where("libraryBookId", "==", dup.id).limit(100).get();
          const batch1 = db.batch();
          feedSnap.docs.forEach(doc => {
            batch1.update(doc.ref, { "libraryBookId": survivor.id });
          });
          if (!feedSnap.empty) await batch1.commit();

          // 2. Subcollection quotes'ları taşı
          const quotesSnap = await db.collection("library_books")
            .doc(dup.id).collection("quotes").limit(100).get();
          for (const qDoc of quotesSnap.docs) {
            await db.collection("library_books").doc(survivor.id)
              .collection("quotes").add({ ...qDoc.data(), bookId: survivor.id });
            await qDoc.ref.delete();
          }

          // 3. Survivor sayaçlarını güncelle
          const totalQuotes  = (survivor.data.quoteCount  || 0) + (dup.data.quoteCount  || 0);
          const totalReviews = (survivor.data.reviewCount || 0) + (dup.data.reviewCount || 0);
          await survivor.ref.update({
            "quoteCount"  : totalQuotes,
            "reviewCount" : totalReviews,
            "titleLower"  : (survivor.data.title || "").toLowerCase().trim(),
          });

          // 4. Duplicate'i sil
          await dup.ref.delete();
          merged++;
        } catch (e) {
          console.error(`merge error ${dup.id}:`, e.message);
          errors++;
        }
      }
    }
  } catch (e) {
    console.error("mergeLibraryBooks error:", e.message);
    errors++;
  }

  res.json({ ok: true, merged, errors, message: `${merged} duplicate kitap birleştirildi` });
});

// ─────────────────────────────────────────────────────────────────────────────
//  onUserDeleted — Auth kullanıcısı silinince Firestore'daki users dokümanını da sil
// ─────────────────────────────────────────────────────────────────────────────
exports.onUserDeleted = functions
  .runWith({})
  .region("europe-west1")
  .auth.user()
  .onDelete(async (user) => {
    const db = getFirestore();
    try {
      await db.collection("users").doc(user.uid).delete();
      console.log(`[onUserDeleted] users/${user.uid} silindi`);
    } catch (e) {
      console.error(`[onUserDeleted] Hata: ${e.message}`);
    }
  });

// ─────────────────────────────────────────────────────────────────────────────
//  cleanOrphanUsers — Auth'ta olmayan Firestore users dokümanlarını temizle (bir kerelik)
//  Kullanım: GET /cleanOrphanUsers?secret=hf2024
// ─────────────────────────────────────────────────────────────────────────────
exports.cleanOrphanUsers = onRequest({ region: "europe-west1" }, async (req, res) => {
  if (req.query.secret !== ADMIN_SECRET) return res.status(403).send("Forbidden");
  const _ipC = req.headers["x-forwarded-for"] || req.ip || "unknown";
  if (!checkIpRateLimit(_ipC, 5)) return res.status(429).send("Rate limit.");

  const db   = getFirestore();
  const auth = require("firebase-admin/auth").getAuth();
  let deleted = 0, errors = 0, checked = 0;

  try {
    // Firestore'daki tüm user dokümanlarını çek (sayfalayarak)
    let lastDoc = null;
    while (true) {
      let query = db.collection("users").limit(100);
      if (lastDoc) query = query.startAfter(lastDoc);
      const snap = await query.get();
      if (snap.empty) break;

      for (const doc of snap.docs) {
        checked++;
        try {
          await auth.getUser(doc.id); // Auth'ta var mı?
        } catch (e) {
          if (e.code === "auth/user-not-found") {
            // Auth'ta yok — Firestore'dan sil
            await doc.ref.delete();
            deleted++;
            console.log(`[cleanOrphan] Silindi: ${doc.id}`);
          } else {
            errors++;
          }
        }
      }

      lastDoc = snap.docs[snap.docs.length - 1];
      if (snap.size < 100) break;
    }
  } catch (e) {
    console.error("cleanOrphanUsers error:", e.message);
    errors++;
  }

  res.json({
    ok: true, checked, deleted, errors,
    message: `${checked} kontrol edildi, ${deleted} sahipsiz doküman silindi`,
  });
});
