# SOS Alert - Backend App

Ushbu loyiha SOS Alert Android dasturi uchun mo'ljallangan ixtiyoriy custom loyiha backend (API) hisoblanadi. U Node.js, Express va Socket.io asosida yozilgan va GitHub-dan to'g'ridan-to'g'ri bulutli hostings xizmatiga ulanib bepul ishlashiga moslashtirilgan.

## Render.com platformasiga Bepul Deploy Qilish (Yuklash) Bosqichlari:

Backendni vizual interfeysli Render.com saytiga hech qanday kdsiz yuklash juda oson. Quyidagi bosqichlarni bajarib chiqing:

### 1-Bosqich: GitHub'ga yuklash
1. Oldin ushbu `backend` papkasini (yoki butun Android loyihangizni) o'zingizning GitHub akkauntingizdagi yangi repozitoriyaga joylang (push qiling).

### 2-Bosqich: Render.com orqali ulanish
1. [Render.com](https://render.com) saytiga o'ting va ro'yxatdan o'ting. Esingizda tuting: **"Sign in with GitHub"** yordamida kirsangiz ishingiz juda yengillashadi.
2. Tizimga kirgach, ekranning o'ng tomonidagi **"New"** (Yangi) tugmasini bosing va ro'yxatdan **"Web Service"** ni tanlang.
3. Ochilgan oynadan **"Build and deploy from a Git repository"** tugmasini bosing.
4. Ulanishni tasdiqlang va GitHub repo'ni qidirib, yoningidagi **"Connect"** (Ulanish) tugmasiga bosing.

### 3-Bosqich: Sozlamalar
Quyidagi maydonlarni e'tibor bilan to'ldiring:
- **Name**: O'zingiz yoqtirgan nom yozing (masalan, `sos-backend-server`).
- **Region**: O'zingizga yaqin mintaqa tanlang (masalan `Frankfurt`).
- **Branch**: `main` (agar master ishlatsangiz, o'zini qo'ying).
- **Root Directory**: Bu juda muhim! `backend` deb yozing (chunki bizning Nodejs serverimiz /backend papkasining ichida).
- **Runtime**: `Node` ni tanlang.
- **Build Command**: `npm install` (aynan shunday yozish kerak).
- **Start Command**: `npm start` (npm start deb yozish kerak).

### 4-Bosqich: Bepul variantni tanlash va Ishga tushirish
1. Sahifaning pastiga bir oz tushing va u yerdan **"Free"** (Bepul, $0/month) turini tanlang.
2. Sahifaning eng oxiridagi ko'k rangli **"Create Web Service"** tugmasini bosing.

🎉 **Tayyor!** Endi biroz kuting (1-3 daqiqa o'tadi).
Render o'z avtomatikasini ishlatib serveringizni ko'taradi. Yashil **"Live"** degan yozuv paydo bo'lgach, sayt tomonidan berilgan `https://nomi.onrender.com/` ssilkasidan (URL) platformangiz manzili sifatida foydalanishingiz mumkin!

---

*Eslatma: Render.com bepul platformasi serveringizga 15 daqiqa davomida hech qanday so'rov kelmasa "uyqu" (sleep) rejimiga o'tadi. Shuning uchun, uzoq uzilishlardan so'ng birinchi kirganda API biroz sekin javob berishi (50 soniyagacha) normal holat, navbatdagi so'rovlar esa tez ishlaydi.*
