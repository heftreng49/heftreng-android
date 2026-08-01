// Android Strings.kt karşılığı — Türkçe / Kurmancî
// Kullanım: import { t, lang } from '$lib/i18n/strings';
//           $t('follow')  →  "Takip Et" veya "Şopîne"

import { readable, derived } from 'svelte/store';

// localStorage'dan dili oku
function getLang(): string {
  if (typeof localStorage === 'undefined') return 'tr';
  return localStorage.getItem('hf_lang') ?? 'tr';
}

// Reaktif dil store'u
function createLangStore() {
  const { subscribe, set } = readable(getLang(), (s) => { s(getLang()); });
  return {
    subscribe,
    set: (l: string) => {
      if (typeof localStorage !== 'undefined') localStorage.setItem('hf_lang', l);
      // html lang attribute
      if (typeof document !== 'undefined') document.documentElement.lang = l === 'ku' ? 'ku' : 'tr';
      set(l);
    },
  };
}

export const lang = createLangStore();

// Çeviri yardımcısı
const ku = (l: string) => l === 'ku';

type L = string;
const tr = (l: L, a: string, b: string) => ku(l) ? b : a;

// ── Tüm string'ler ─────────────────────────────────────────────────────────
export const strings = {
  // Nav
  navFeed:       (l: L) => tr(l, 'Gönderi',       'Nivis'),
  navLibrary:    (l: L) => tr(l, 'Kütüphane',     'Pirtûkxane'),
  navKurdi:      (l: L) => tr(l, 'Kurdî',         'Kurdî'),
  navProfile:    (l: L) => tr(l, 'Profil',        'Profîl'),
  navSearch:     (l: L) => tr(l, 'Keşfet',        'Vedîtin'),
  navMessages:   (l: L) => tr(l, 'Mesajlar',      'Peyam'),
  navNotifs:     (l: L) => tr(l, 'Bildirimler',   'Agahî'),
  navSettings:   (l: L) => tr(l, 'Ayarlar',       'Mîheng'),

  // Genel
  save:          (l: L) => tr(l, 'Kaydet',         'Tomar bike'),
  ok:            (l: L) => tr(l, 'Tamam',          'Temam'),
  cancel:        (l: L) => tr(l, 'İptal',          'Betal bike'),
  delete:        (l: L) => tr(l, 'Sil',            'Jê bibe'),
  edit:          (l: L) => tr(l, 'Düzenle',        'Biguhêze'),
  send:          (l: L) => tr(l, 'Gönder',         'Bişîne'),
  back:          (l: L) => tr(l, 'Geri',           'Vegere'),
  loading:       (l: L) => tr(l, 'Yükleniyor…',   'Tê barkirin…'),
  close:         (l: L) => tr(l, 'Kapat',          'Bigire'),
  share:         (l: L) => tr(l, 'Paylaş',         'Parve bike'),
  noResult:      (l: L) => tr(l, 'Sonuç bulunamadı', 'Encam nehate dîtin'),
  error:         (l: L) => tr(l, 'Bir hata oluştu', 'Çewtiyeke derket'),
  readMore:      (l: L) => tr(l, 'Devamını Oku',   'Zêdetir bixwîne'),
  showLess:      (l: L) => tr(l, 'Daha Az Göster', 'Kêmtir Nîşan Bide'),
  copied:        (l: L) => tr(l, 'Kopyalandı',     'Kopî bû'),
  copy:          (l: L) => tr(l, 'Kopyala',        'Kopî bike'),

  // Sosyal
  follow:        (l: L) => tr(l, 'Takip Et',       'Şopîne'),
  unfollow:      (l: L) => tr(l, 'Takibi Bırak',   'Şopînê berde'),
  followers:     (l: L) => tr(l, 'Takipçi',        'Şopîner'),
  following:     (l: L) => tr(l, 'Takip',          'Şopandî'),
  followReqSent: (l: L) => tr(l, 'İstek Gönderildi', 'Daxwaz Şand'),
  like:          (l: L) => tr(l, 'Beğen',          'Hez bike'),
  likes:         (l: L) => tr(l, 'Beğeni',         'Hez kirin'),
  comment:       (l: L) => tr(l, 'Yorum',          'Şîrove'),
  addComment:    (l: L) => tr(l, 'Yorum Ekle',     'Şîrove zêde bike'),
  commentHint:   (l: L) => tr(l, 'Yorumunu yaz…', 'Şîroveya xwe binivîse…'),
  noComments:    (l: L) => tr(l, 'Henüz yorum yok', 'Hîn şîrove tune'),
  report:        (l: L) => tr(l, 'Şikayet Et',     'Rapor bike'),
  repost:        (l: L) => tr(l, 'Tekrar Paylaş',  'Dîsa parve bike'),

  // Auth
  login:         (l: L) => tr(l, 'Giriş Yap',      'Têkeve'),
  logout:        (l: L) => tr(l, 'Çıkış Yap',      'Derkeve'),
  register:      (l: L) => tr(l, 'Kayıt Ol',       'Qeyd bibe'),
  email:         (l: L) => tr(l, 'E-posta',         'E-name'),
  password:      (l: L) => tr(l, 'Şifre',           'Şîfre'),
  fullName:      (l: L) => tr(l, 'Ad Soyad',        'Nav û Nasname'),
  username:      (l: L) => tr(l, 'Kullanıcı Adı',  'Navê Bikarhêner'),
  noAccount:     (l: L) => tr(l, 'Hesabın yok mu? Kayıt ol', 'Hesabê te tune? Qeyd bibe'),
  hasAccount:    (l: L) => tr(l, 'Hesabın var mı? Giriş yap', 'Hesabê te heye? Têkeve'),
  orDivider:     (l: L) => tr(l, 'ya da',           'an jî'),
  googleLogin:   (l: L) => tr(l, 'Google ile devam et', 'Bi Google re berdewam bike'),

  // Feed
  whatsOnMind:   (l: L) => tr(l, 'Ne düşünüyorsun?', 'Tu çi difikiri?'),
  postHint:      (l: L) => tr(l, 'Düşüncelerini paylaş…', 'Ramanên xwe parve bike…'),
  noPosts:       (l: L) => tr(l, 'Henüz gönderi yok', 'Hîn nivîs tune'),
  deletePost:    (l: L) => tr(l, 'Gönderiyi Sil',  'Nivîsê jê bibe'),
  filterAll:     (l: L) => tr(l, 'Herkes',          'Hemû'),
  filterFollowing:(l:L) => tr(l, 'Takip Edilenler', 'Şopandî'),

  // Profil
  editProfile:   (l: L) => tr(l, 'Profili Düzenle', 'Profîlê biguhêze'),
  bio:           (l: L) => tr(l, 'Hakkında',        'Der barê min'),
  website:       (l: L) => tr(l, 'Web Sitesi',      'Malpera min'),
  joined:        (l: L) => tr(l, 'Katıldı',         'Beşdar bû'),
  savedPosts:    (l: L) => tr(l, 'Kaydedilenler',   'Tomarkirî'),
  posts:         (l: L) => tr(l, 'Gönderi',         'Nivîs'),
  booksRead:     (l: L, n: number) => tr(l, `${n} kitap okudum`, `${n} pirtûk min xwendin`),
  quoteCount:    (l: L, n: number) => tr(l, `${n} alıntı`, `${n} jêgirt`),

  // Bildirimler
  notifTitle:    (l: L) => tr(l, 'Bildirimler',    'Agahdarî'),
  notifUnread:   (l: L, n: number) => tr(l, `${n} okunmamış`, `${n} nexwendî`),
  notifMarkAll:  (l: L) => tr(l, 'Tümünü oku',     'Hemû bixwîne'),
  notifEmpty:    (l: L) => tr(l, 'Henüz bildirim yok', 'Agahdarî tune'),
  today:         (l: L) => tr(l, 'Bugün',           'Îro'),
  thisWeek:      (l: L) => tr(l, 'Bu Hafta',        'Vê Hefteyê'),
  older:         (l: L) => tr(l, 'Daha Eski',       'Berê'),

  // Mesajlar
  messages:      (l: L) => tr(l, 'Mesajlar',        'Peyam'),
  searchMsgs:    (l: L) => tr(l, 'Konuşma ara…',   'Axaftinê bigere…'),
  newConv:       (l: L) => tr(l, 'Yeni Konuşma',   'Axaftinek Nû'),
  noMessages:    (l: L) => tr(l, 'Henüz mesaj yok', 'Peyam tune'),
  msgHint:       (l: L) => tr(l, 'Mesaj yaz…',     'Peyamê binivîse…'),
  msgDeleted:    (l: L) => tr(l, 'Mesaj silindi',   'Peyam hate jêbirin'),
  online:        (l: L) => tr(l, 'Çevrimiçi',       'Serhêl'),

  // Kütüphane
  library:       (l: L) => tr(l, 'Kütüphane',      'Pirtûkxane'),
  quotes:        (l: L) => tr(l, 'Alıntılar',      'Jêgirt'),
  reviews:       (l: L) => tr(l, 'İncelemeler',    'Nirxandin'),
  authors:       (l: L) => tr(l, 'Yazarlar',       'Nivîskar'),
  books:         (l: L) => tr(l, 'Kitaplar',       'Pirtûk'),
  noQuotes:      (l: L) => tr(l, 'Henüz alıntı yok', 'Hîn jêgirt tune'),
  noReviews:     (l: L) => tr(l, 'Henüz inceleme yok', 'Hîn nirxandin tune'),
  noAuthors:     (l: L) => tr(l, 'Henüz yazar yok', 'Hîn nivîskar tune'),
  noBooks:       (l: L) => tr(l, 'Henüz kitap yok', 'Hîn pirtûk tune'),
  loadMore:      (l: L) => tr(l, 'Daha fazla göster', 'Zêdetir nîşan bide'),

  // Ayarlar
  settings:      (l: L) => tr(l, 'Ayarlar',        'Mîheng'),
  appearance:    (l: L) => tr(l, 'Görünüm',        'Xuyang'),
  language:      (l: L) => tr(l, 'Dil',            'Ziman'),
  account:       (l: L) => tr(l, 'Hesap',          'Hesab'),
  privacy:       (l: L) => tr(l, 'Gizlilik',       'Nepenî'),
  notifications: (l: L) => tr(l, 'Bildirimler',    'Agahdarî'),
  darkMode:      (l: L) => tr(l, 'Koyu Mod',       'Moda Tarî'),
  lightMode:     (l: L) => tr(l, 'Açık Mod',       'Moda Ronî'),
  systemMode:    (l: L) => tr(l, 'Sistem',         'Pergal'),
  colorTheme:    (l: L) => tr(l, 'Renk Teması',    'Rengê Temayê'),
  privateAccount:(l: L) => tr(l, 'Gizli Hesap',   'Hesabê Veşartî'),
  msgPermission: (l: L) => tr(l, 'Mesaj İzni',     'Destûra Peyamê'),
  everyone:      (l: L) => tr(l, 'Herkes',         'Hemû'),
  onlyFollowers: (l: L) => tr(l, 'Takipçiler',     'Şopîner'),
  nobody:        (l: L) => tr(l, 'Kimse',          'Kes'),
  changePassword:(l: L) => tr(l, 'Şifre Değiştir', 'Şîfreyê Biguherîne'),
  blockedUsers:  (l: L) => tr(l, 'Engellenen Kullanıcılar', 'Bikarhênerên Astengkirî'),
  unblock:       (l: L) => tr(l, 'Engeli Kaldır',  'Astengê rake'),
  deleteAccount: (l: L) => tr(l, 'Hesabı Sil',     'Hesabê jê bibe'),
  signOut:       (l: L) => tr(l, 'Çıkış Yap',      'Derkeve'),

  // Arama
  search:        (l: L) => tr(l, 'Ara',            'Bigere'),
  searchHint:    (l: L) => tr(l, 'Ara…',           'Bigere…'),
  suggestedUsers:(l: L) => tr(l, 'Önerilen Kişiler', 'Kesên Pêşniyarkirî'),
  people:        (l: L) => tr(l, 'Kişiler',        'Kes'),
};

// Reaktif çeviri — store ile kullanım:
// const $l = lang; // store'dan gelen değer
// {strings.follow($l)}
