# ⚠️ Firebase Console'da Yapılacak İşlem

## isAdmin UID Kurulumu (Zorunlu)

firestore.rules artık e-posta yerine `admins` koleksiyonunu kontrol ediyor.
Enforce etmeden önce şunu yapman gerekiyor:

1. Firebase Console → Firestore Database → "admins" koleksiyonu oluştur
2. Document ID olarak kendi UID'ini gir (Authentication → Users'dan bulabilirsin)
3. İçine herhangi bir alan ekle, örneğin: `role: "admin"`

Örnek:
  Koleksiyon: admins
  Döküman ID: abc123xyz (senin UID'in)
  Alan: role = "admin"

Bu yapılmazsa admin paneline erişim tamamen kapanır!
