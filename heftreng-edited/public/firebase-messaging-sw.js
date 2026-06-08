/* ═══════════════════════════════════════════════════
   Heftreng — Firebase Messaging Service Worker
   GitHub Pages'e yükle: heftreng49.github.io/firebase-messaging-sw.js
   ═══════════════════════════════════════════════════ */

importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey:            "AIzaSyCZByW_n4B888Ec4cjNQDoQovU-rVN75gs",
  authDomain:        "bloggerheftreng.firebaseapp.com",
  projectId:         "bloggerheftreng",
  storageBucket:     "bloggerheftreng.firebasestorage.app",
  messagingSenderId: "854520441903",
  appId:             "1:854520441903:web:3972b80e9c97d922cfaada"
});

const messaging = firebase.messaging();

/* ── Arka planda gelen push bildirimleri ── */
messaging.onBackgroundMessage(function(payload) {
  const data        = payload.data || payload.notification || {};
  const title       = data.title  || 'Heftreng';
  const body        = data.body   || 'Yeni bildirim';
  const icon        = data.icon   || 'https://raw.githubusercontent.com/heftreng49/depo/master/icons/icon-192.png';
  const badge       = data.badge  || 'https://raw.githubusercontent.com/heftreng49/depo/master/icons/icon-72.png';
  const url         = data.url    || 'https://heft-reng.blogspot.com';
  const tag         = data.tag    || 'heftreng-notif';

  return self.registration.showNotification(title, {
    body,
    icon,
    badge,
    tag,
    renotify: true,
    vibrate: [200, 100, 200],
    data: { url }
  });
});

/* ── Bildirime tıklanınca ilgili sayfaya git ── */
self.addEventListener('notificationclick', function(e) {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || 'https://heft-reng.blogspot.com';
  e.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(list) {
      for (var c of list) {
        if (c.url === url && 'focus' in c) return c.focus();
      }
      return clients.openWindow(url);
    })
  );
});
