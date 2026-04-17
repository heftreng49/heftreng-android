# Heftreng Android — Native

Native Google Sign-In + Firebase FCM bildirimleri. WebView yok.

## GitHub'a Gönderme (Termux)

```bash
# 1. Termux'ta git kur (yoksa)
pkg install git

# 2. Projeyi klonla veya klasörü oluştur
cd ~
git clone https://github.com/KULLANICI/REPO.git heftreng
cd heftreng

# 3. Bu zip'teki dosyaları kopyala, sonra:
git add .
git commit -m "Native Android app - Google Sign-In + FCM"
git push origin main
```

## gradle-wrapper.jar Eksikse

GitHub Actions build yaparken otomatik indirir, sorun olmaz.
Ama local build için Termux'ta:

```bash
# gradle-wrapper.jar indir
mkdir -p gradle/wrapper
curl -L "https://services.gradle.org/distributions/gradle-8.4-bin.zip" \
     -o /tmp/gradle.zip
# VEYA doğrudan jar:
curl -L "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar" \
     -o gradle/wrapper/gradle-wrapper.jar
```

## Kurulum (Android Studio)

1. `app/google-services.json` dosyasını Firebase Console'dan indirip `app/` klasörüne koy
2. `app/src/main/res/values/strings.xml` dosyasına ekle:
   ```xml
   <string name="default_web_client_id">BURAYA_WEB_CLIENT_ID</string>
   ```
   (Firebase Console > Authentication > Sign-in method > Google > Web Client ID)
3. `res/mipmap-*` klasörlerine uygulama ikonunu ekle

## GitHub Secrets (Actions için)

| Secret | Açıklama |
|--------|----------|
| `GOOGLE_SERVICES_JSON` | google-services.json dosyasının içeriği |
| `KEYSTORE_BASE64` | İmzalama keystore (base64) |
| `KEYSTORE_PASSWORD` | Keystore şifresi |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key şifresi |

## FCM Bildirimi Gönderme

```json
{
  "data": {
    "title": "Yeni yorum",
    "body": "Yazınıza yorum yapıldı",
    "type": "comment",
    "postId": "123"
  }
}
```

`type` değerleri: `comment`, `like`, `follow`, `general`
