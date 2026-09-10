#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# codespaces-ilk-kurulum.sh
#
# SADECE 1 KEZ çalıştırılır — web/ios/ klasörünü oluşturur.
# Bundan sonra GitHub Actions cap sync ile günceller.
#
# Nerede çalıştırılır:
#   GitHub → Repon → "Code" → "Codespaces" → "New codespace"
#   Terminal açılır, bu script çalıştırılır.
#
# Çalıştırma:
#   bash codespaces-ilk-kurulum.sh
# ═══════════════════════════════════════════════════════════════════

set -e  # Hata olursa dur

echo "📦 Web bağımlılıkları yükleniyor..."
cd web
npm install --legacy-peer-deps

echo "🏗️  SvelteKit build alınıyor (web/build/ üretiliyor)..."
# Codespaces'te env yoksa boş bırak — build klasörü oluşması yeterli
npm run build || true

echo "⚡ Capacitor başlatılıyor..."
# capacitor.config.ts zaten var, bu komut sadece ios platform ekler
npx cap add ios

echo "🔄 Capacitor sync (web/build/ → web/ios/)..."
npx cap sync ios

echo ""
echo "✅ web/ios/ klasörü oluşturuldu!"
echo ""
echo "📤 Git'e ekleniyor..."
cd ..
git add web/ios web/package-lock.json
git status

echo ""
echo "Şimdi şunu çalıştır:"
echo "  git commit -m 'feat(ios): Capacitor iOS projesi eklendi'"
echo "  git push"
echo ""
echo "Sonra GitHub Actions otomatik olarak her build'i yapacak."
