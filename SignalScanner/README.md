# Signal Scanner - Android App

App nativa Android che rileva segnali reali dal tuo dispositivo:
- 📶 **Wi-Fi** — Scansiona tutte le reti wireless nelle vicinanze
- 🔵 **Bluetooth** — Rileva dispositivi Bluetooth vicini  
- 📱 **Cellulare** — Mostra le torri cellulari (2G/3G/4G/5G) connesse
- 🛰️ **GPS** — Elenca i satelliti visibili (GPS, GLONASS, Galileo, BeiDou)

## Come buildare l'APK gratis

### Opzione 1: GitHub + GitHub Actions (Consigliato)
1. Crea un account su https://github.com (gratis)
2. Crea un nuovo repository e carica questa cartella
3. Aggiungi il file `.github/workflows/build.yml` (incluso nel progetto)
4. GitHub compilerà automaticamente l'APK ad ogni push
5. Scarica l'APK dalla sezione "Actions" > "Artifacts"

### Opzione 2: Replit
1. Vai su https://replit.com (gratis)
2. Crea un nuovo Repl "Android" e carica i file
3. Esegui il build con Gradle

### Opzione 3: Gitpod
1. Vai su https://gitpod.io
2. Apri il tuo repository GitHub con Gitpod
3. Esegui: `./gradlew assembleDebug`
4. L'APK sarà in `app/build/outputs/apk/debug/`

## Requisiti dispositivo
- Android 7.0+ (API 24)
- Permessi: Posizione, Wi-Fi, Bluetooth, Telefono
