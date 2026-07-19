// Strings.kt karşılığı — TR/KU i18n
export type Lang = "tr" | "ku"

const t = (lang: Lang, tr: string, ku: string) => lang === "ku" ? ku : tr

export const Strings = {
  // Navigasyon
  navFeed:          (l: Lang) => t(l, "Gönderi",        "Nivis"),
  navBlog:          (l: Lang) => t(l, "Blog",            "Blog"),
  navBooks:         (l: Lang) => t(l, "Kitaplar",        "Pirtûk"),
  navLibrary:       (l: Lang) => t(l, "Kütüphane",       "Pirtûkxane"),
  navKurdi:         (l: Lang) => t(l, "Kurdî",           "Kurdî"),
  navProfile:       (l: Lang) => t(l, "Profil",          "Profîl"),
  navSearch:        (l: Lang) => t(l, "Keşfet",          "Keşif bike"),
  navMessages:      (l: Lang) => t(l, "Mesajlar",        "Peyam"),
  navNotifs:        (l: Lang) => t(l, "Bildirimler",     "Agahî"),
  navSettings:      (l: Lang) => t(l, "Ayarlar",         "Mîheng"),
  navSerials:       (l: Lang) => t(l, "Seriler",         "Rêze"),
  navSaved:         (l: Lang) => t(l, "Kaydedilenler",   "Tomarkirî"),

  // Genel
  save:             (l: Lang) => t(l, "Kaydet",          "Tomar bike"),
  ok:               (l: Lang) => t(l, "Tamam",           "Temam"),
  cancel:           (l: Lang) => t(l, "İptal",           "Betal bike"),
  delete:           (l: Lang) => t(l, "Sil",             "Jê bibe"),
  edit:             (l: Lang) => t(l, "Düzenle",         "Biguhêze"),
  send:             (l: Lang) => t(l, "Gönder",          "Bişîne"),
  back:             (l: Lang) => t(l, "Geri",            "Vegere"),
  loading:          (l: Lang) => t(l, "Yükleniyor...",   "Tê barkirin..."),
  retry:            (l: Lang) => t(l, "Tekrar Dene",     "Dîsa biceribîne"),
  close:            (l: Lang) => t(l, "Kapat",           "Bigire"),
  share:            (l: Lang) => t(l, "Paylaş",          "Parve bike"),
  noResult:         (l: Lang) => t(l, "Sonuç bulunamadı","Encam nehate dîtin"),
  error:            (l: Lang) => t(l, "Bir hata oluştu", "Çewtiyeke derket"),
  copy:             (l: Lang) => t(l, "Kopyala",         "Kopî bike"),
  copied:           (l: Lang) => t(l, "Kopyalandı",      "Kopî bû"),

  // Sosyal
  follow:           (l: Lang) => t(l, "Takip Et",        "Şopîne"),
  unfollow:         (l: Lang) => t(l, "Takibi Bırak",    "Şopînê berde"),
  followers:        (l: Lang) => t(l, "Takipçi",         "Şopîner"),
  following:        (l: Lang) => t(l, "Takip Edilen",    "Tên şopandin"),
  like:             (l: Lang) => t(l, "Beğen",           "Hez bike"),
  comment:          (l: Lang) => t(l, "Yorum",           "Şîrove"),
  repost:           (l: Lang) => t(l, "Paylaş",          "Ji nû ve parve bike"),
  quote:            (l: Lang) => t(l, "Alıntıla",        "Jê bêje"),

  // Auth
  login:            (l: Lang) => t(l, "Giriş Yap",       "Têkevin"),
  logout:           (l: Lang) => t(l, "Çıkış Yap",       "Derkevin"),
  register:         (l: Lang) => t(l, "Kayıt Ol",        "Tomar bibe"),
  email:            (l: Lang) => t(l, "E-posta",         "E-peyam"),
  password:         (l: Lang) => t(l, "Şifre",           "Şîfre"),
  username:         (l: Lang) => t(l, "Kullanıcı Adı",   "Navê bikarhêner"),
  forgotPassword:   (l: Lang) => t(l, "Şifremi Unuttum", "Şîfreya min ji bîr bû"),

  // Feed
  writePost:        (l: Lang) => t(l, "Ne düşünüyorsun?","Tu çi difikirinî?"),
  postEmpty:        (l: Lang) => t(l, "Henüz gönderi yok","Hêj nivis tune"),
  newPosts:         (l: Lang) => t(l, "Yeni gönderi var", "Nivisên nû hene"),

  // Tema
  themeCharcoal:    (l: Lang) => t(l, "Mürekkep Karası", "Mûrekkeba Komirê"),
  themeBook:        (l: Lang) => t(l, "Kitap",           "Pirtûk"),
  themeDaristan:    (l: Lang) => t(l, "Orman",           "Daristan"),
  themeOcean:       (l: Lang) => t(l, "Okyanus",         "Okyanûs"),
  themeSunset:      (l: Lang) => t(l, "Gün Batımı",      "Rojavabûn"),
  themeMono:        (l: Lang) => t(l, "Tek Renk",        "Yek Reng"),
  dark:             (l: Lang) => t(l, "Karanlık",        "Tarî"),
  light:            (l: Lang) => t(l, "Aydınlık",        "Ronî"),
  system:           (l: Lang) => t(l, "Sistem",          "Sîstem"),
}
