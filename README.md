# Devis Chantier

Application Android de devis pour artisans : catalogue d'articles par familles,
création et suivi des devis, dictée vocale, envoi au client, transmission au
comptable ou à l'assistante administrative.

Cahier des charges : voir `/root/projects/cahiers-des-charges` (document de
référence, novembre 2026).

## Ce que fait l'application (v1.0.0)

- **Devis** : numérotation automatique (`D-2026-0001`), lignes avec sections,
  TVA par taux, remise, acompte, aperçu PDF sur le téléphone, verrouillage après
  envoi et versions B, C… pour garder la trace de ce que le client a reçu.
- **Mentions obligatoires** contrôlées avant envoi : identité, SIREN, validité,
  assurance professionnelle (avec rappel avant expiration de l'attestation),
  mention de TVA calculée selon la date du devis (art. 293 B du CGI jusqu'au
  31/12/2026, puis art. L. 233-3 du CIBS), rétractation 14 jours, médiateur.
- **Articles** : familles et sous-familles, synonymes pour la dictée, favoris,
  import/export CSV, et **catalogues de départ par métier** (plombier,
  électricien, peintre, carreleur, menuisier).
- **Clients** : saisie manuelle ou remplissage depuis l'API Recherche
  d'entreprises de l'État (nom, SIREN, SIRET), choix de l'établissement, alerte
  si l'entreprise est radiée, TVA intracommunautaire calculée.
- **Dictée** : moteur vocal Android + rapprochement catalogue par règles
  (gratuit, hors ligne), et mode assisté par IA avec la clé de l'artisan.
  Rien n'est ajouté à un devis sans validation.
- **Envoi** : PDF vers l'application mail du téléphone (ou SMS, messagerie,
  Drive), données du devis (JSON + CSV) pour le comptable, journal des envois.
- **Rappels** : notifications de fin de validité (Relancer / Plus tard /
  Marquer refusé), relance intermédiaire, une seule notification récapitulative.
- **Marge** : achats distincts du catalogue, liens article ↔ achats, marge
  prévue, taux de marge, taux de marque, coefficient. Jamais sur le PDF client.
- **Sauvegarde** : archive chiffrée (PBKDF2 + AES-GCM) avec phrase secrète,
  export manuel et sauvegarde automatique quotidienne dans un dossier choisi
  (un dossier Google Drive via l'application Fichiers convient).
- **Raccourci d'écran d'accueil** « Devis vocal » : un appui, dictée directe.
- **Mise à jour automatique** par GitHub Releases, même mécanisme que
  l'application Notes de frais : release roulante `latest` + `version.json`.

## Données et vie privée

Tout est local (SQLite/Room). Aucun compte, aucun serveur. L'application ne
transmet des données qu'à trois moments choisis par l'artisan : recherche d'une
entreprise (API publique de l'État), envoi d'un devis par son application mail,
et dictée assistée si une clé API est renseignée. Les prix d'achat et les marges
ne partent jamais chez un fournisseur d'IA.

## Technique

- Kotlin + Jetpack Compose, minSdk 26, targetSdk 35.
- Room pour la base locale, WorkManager pour les rappels, PdfDocument pour les
  PDF, OkHttp pour le téléchargement des mises à jour.
- 112 tests unitaires JVM (`./gradlew testDebugUnitTest`).
- QA visuelle sur émulateur Android dans la CI (`scripts/smoke-test.sh`).

## Compiler et installer

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew testDebugUnitTest assembleRelease   # APK signé si le keystore est présent
```

Le keystore de signature vit hors du dépôt (`/root/.secrets/keystores-android/`)
et ses mots de passe passent par des secrets GitHub. Sans lui, `assembleRelease`
produit un APK non signé : l'installation par-dessus une version existante
échouerait.

Téléchargement de la dernière version :
`https://github.com/ClawFabriceH92/devis-chantier/releases/download/latest/app-release.apk`

## Lots suivants

Lot 2 (reste) : métrés simples, photos annotées. Lot 3 : avenants, acomptes et
avancement, tableau de bord. Lot 4 : serveur d'envoi, synchronisation, plusieurs
utilisateurs, retour du comptable.
