# Liftrix — Materiale pentru jurizare InfoEducație 2026

Acest folder conține materialele pentru evaluarea proiectului Liftrix la secțiunea Software Utilitar / Mobile, în cadrul fazei naționale InfoEducație 2026.

Versiunea declarată în cod la actualizarea materialelor este **1.0.1** (`versionCode 10001`). Aplicația este compatibilă cu Android 8.0 / API 26 sau mai nou.

## Fișiere incluse

- `Liftrix_Documentatie.docx` — documentația tehnică a proiectului
- `Liftrix_InfoEducatie_2026.pptx` — prezentarea pentru susținere
- `DECLARATIE_RESURSE_EXTERNE.txt` — biblioteci, servicii externe, resurse folosite, utilizarea instrumentelor AI și contribuția autorului
- `dovezi/` — capturi Analytics istorice din intervalul aprilie–mai 2026

Folderul nu include automat un APK. Versiunea instalabilă se descarcă din pagina de release indicată mai jos.

## Linkuri utile

- Repository cod sursă: https://github.com/valentin5643/LiftrixApp
- APK / Release: https://github.com/valentin5643/LiftrixApp/releases
- Demo video: https://drive.google.com/file/d/138EH_MFxiS6wFZGwY7s6fRI6sg261IJI/view?usp=drivesdk

## Rulare rapidă pentru juriu

1. Descărcați APK-ul din linkul de release.
2. Instalați aplicația pe un dispozitiv Android 8.0 / API 26 sau mai nou.
3. Creați un cont sau folosiți contul de test pus la dispoziție.
4. Testați fluxul principal:
   - porniți sau continuați un antrenament;
   - adăugați seturi, repetări și greutăți;
   - finalizați antrenamentul și verificați dashboardul de progres;
   - deschideți widget-urile analitice și widget-urile Android native;
   - testați feedul social, nivelurile de confidențialitate și Gym Buddy prin QR;
   - testați partajarea unui șablon de antrenament.
5. Opțional, pe contul de concurs autorizat, testați chatul AI și constructorul ghidat de antrenamente. Funcționalitățile AI cu cost sunt controlate prin Remote Config și App Check și sunt disponibile numai conturilor autorizate.

Fluxul principal al aplicației poate fi demonstrat fără serviciul AI.

## Rulare din sursă

1. Clonați repository-ul.
2. Deschideți proiectul în Android Studio, folosind JDK 17.
3. Adăugați privat fișierul `google-services.json` în folderul `app/` pentru serviciile Firebase. Fișierul nu trebuie publicat, introdus în documentație sau afișat în capturi de ecran.
4. Compilați build-ul debug cu:

   ```bash
   ./gradlew assembleDebug
   ```

5. Rulați aplicația pe un emulator sau pe un dispozitiv Android API 26+.

Pentru o verificare mai rapidă a sursei Kotlin se poate folosi:

```bash
./gradlew compileDebugKotlin
```

## Funcționalități principale prezentate

- urmărirea antrenamentelor și recuperarea sesiunii active;
- șabloane, foldere și exerciții personalizate;
- bibliotecă locală cu 100 de exerciții;
- dashboard de progres cu 12 widget-uri analitice configurabile;
- widget-uri Android native pentru Streak, Consistency și Dashboard;
- feed social offline-first, confidențialitate Public / Followers / Private și interacțiuni optimiste;
- Gym Buddy, asociere prin QR temporar și partajarea șabloanelor;
- sincronizare Room-first prin coadă locală și WorkManager;
- chat AI și constructor ghidat de antrenamente, disponibile în condițiile de autorizare descrise mai sus;
- setări pentru notificări, sincronizare, widget-uri, ajutor și portabilitatea datelor.

Google Play Billing este inclus ca infrastructură, însă interfața de upgrade este în regim preview/fail-closed și nu este prezentată ca sistem complet de abonamente. Importul datelor și generarea generală de PDF nu sunt prezentate ca funcționalități finalizate.

## Despre dovezile Analytics

Capturile din `dovezi/` sunt dovezi istorice din aprilie–mai 2026:

- `28m34s` reprezintă timpul mediu de interacțiune per utilizator activ;
- `6,6` reprezintă numărul de sesiuni implicate per utilizator activ.

Aceste valori nu reprezintă retenția și nu sunt prezentate drept statistici curente pentru faza națională.

## Transparență privind resursele externe

Declarația completă privind bibliotecile, serviciile externe, resursele de design, imaginile generate și utilizarea instrumentelor AI se află în `DECLARATIE_RESURSE_EXTERNE.txt`.

Proiectul este realizat individual de Jianu Valentin. Framework-urile, bibliotecile și serviciile externe sunt folosite ca infrastructură, iar logica aplicației, integrarea, arhitectura, UI-ul și testarea sunt asumate de autor.

Codul publicat în repository este însoțit de licența MIT.
