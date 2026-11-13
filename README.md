# Devis Chantier

Application Android de devis pour artisans : catalogue d'articles par familles,
création et suivi des devis, dictée vocale, envoi au client, transmission au
comptable ou à l'assistante administrative.

Cahier des charges : voir `/root/projects/cahiers-des-charges` (document de
référence, novembre 2026).

## Ce que fait l'application (v1.1.0)

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
  (gratuit, hors ligne). Compréhension assistée au choix : **modèle de langage
  embarqué sur le téléphone** (LFM3-1.7B de Liquid AI via llama.cpp, hors
  ligne, gratuit, 1 Go téléchargé une fois), Claude (Anthropic) ou un service
  compatible OpenAI avec la clé de l'artisan. Rien n'est ajouté à un devis sans
  validation.
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

## Dictée assistée sur le téléphone (v1.1)

Le fournisseur par défaut de la dictée assistée est désormais un modèle qui
tourne **sur le téléphone**, sans réseau ni compte :

- **Modèle** : [LFM3-1.7B](https://huggingface.co/LiquidAI/LFM3-1.7B) de Liquid
  AI (famille LFM3 publiée le 27 octobre 2026), fichier GGUF quantifié Q4_0 par
  distillation (`LFM3-1.7B-QAD-Q4_0.gguf`, 1 049 615 680 octets, dépôt public
  [LiquidAI/LFM3-1.7B-GGUF](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF)).
  Le français fait partie des langues annoncées par l'éditeur. L'empreinte
  SHA-256 du fichier reçu est vérifiée avant toute utilisation.
- **Licence du modèle** : LFM Open License v1.0 (dérivée d'Apache 2.0) : usage
  libre, y compris commercial, pour une entité dont le chiffre d'affaires annuel
  est inférieur à 10 millions de dollars. Le fichier LICENSE est publié avec le
  modèle. L'application ne redistribue pas le modèle : l'artisan le télécharge
  lui-même depuis les réglages.
- **Moteur** : [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT),
  étiquette `b12361`, compilé dans l'APK pour `arm64-v8a` (toutes les variantes
  CPU : la meilleure est choisie au démarrage selon le processeur) et `x86_64`
  (émulateur). Liquid AI a déprécié son SDK Android « LEAP » et recommande
  d'embarquer llama.cpp directement.
- **Méthode** : les règles locales présélectionnent les articles du catalogue qui
  ressemblent à la dictée (24 au plus), le modèle reçoit un prompt court et
  répond sous **grammaire GBNF** (tableau JSON aux clés fixes : jamais de texte
  libre), puis le rapprochement local reprend les prix du catalogue. En cas
  d'échec ou de délai dépassé (90 s), les règles seules fournissent le résultat.
- **Prérequis** : téléphone 64 bits, 3 Go de RAM au moins, 1,3 Go d'espace
  libre. Le téléchargement se fait en Wi-Fi sauf autorisation explicite ; il
  reprend là où il s'était arrêté. Un fichier `.gguf` peut aussi être importé à
  la main.
- **Non vérifié à ce jour** : la qualité réelle des extractions sur téléphone
  (le modèle n'a pas pu être exécuté dans l'environnement de développement).
  Le bouton « Tester la dictée sur le téléphone » des réglages sert à cela.

## Données et vie privée

Tout est local (SQLite/Room). Aucun compte, aucun serveur. L'application ne
transmet des données qu'à trois moments choisis par l'artisan : recherche d'une
entreprise (API publique de l'État), envoi d'un devis par son application mail,
et dictée assistée **si un fournisseur cloud est choisi** avec une clé API. Avec
le modèle embarqué, la dictée ne quitte pas le téléphone. Les prix d'achat et
les marges ne partent jamais, dans aucun des cas.

## Technique

- Kotlin + Jetpack Compose, minSdk 26, targetSdk 35.
- Room pour la base locale, WorkManager pour les rappels, PdfDocument pour les
  PDF, OkHttp pour le téléchargement des mises à jour et du modèle.
- Couche native : NDK 27.0.12077973, CMake 3.22.1, `app/src/main/cpp`
  (llama.cpp récupéré par `FetchContent` à la configuration, pont JNI
  `devis_llm.cpp`). Les bibliothèques natives sont extraites à l'installation
  (`useLegacyPackaging`) pour que ggml choisisse la variante CPU.
- Tests unitaires JVM (`./gradlew testDebugUnitTest`), dont le module IA local
  (grammaire, présélection, relecture, en-tête GGUF, reprise de téléchargement).
- QA visuelle sur émulateur Android dans la CI (`scripts/smoke-test.sh`).

## Corrections de la v1.1

Revue complète du code (novembre 2026). Principales corrections :

- PDF : totaux, mentions obligatoires et bloc signature paginés (les mentions
  n'étaient plus imprimées au-delà d'une vingtaine de lignes) ; adresse du
  client correctement mise en page ; signature du client reprise dans le PDF.
- Franchise en base de TVA : plus aucune TVA calculée sur les lignes (le PDF
  affichait une TVA tout en la déclarant « non applicable »).
- Statuts : un devis ne passe « envoyé » ou « transmis » que si le document a
  pu être préparé ; un devis accepté ou facturé renvoyé garde son statut ; la
  signature sur place d'un brouillon passe par le contrôle des mentions.
- Saisie : les champs reliés aux réglages ne reviennent plus en arrière pendant
  la frappe ; l'écran de devis sérialise ses écritures et recalcule les totaux.
- Sauvegarde : écriture possible dans le dossier choisi (URI d'arborescence),
  réglages inclus dans l'archive (jamais les secrets), restauration vérifiée et
  atomique suivie d'un redémarrage, notification si la copie automatique échoue.
- Numérotation en transaction (plus de doublon sur double appui ou rotation),
  indice de version calculé sur le plus grand existant, prix du catalogue de
  départ arrondis au centime, mention « TVA à 5,5 % », rappels : seules les
  notifications respectent la plage horaire, identifiants d'achats corrigés.

## Compiler et installer

```bash
export ANDROID_HOME=/opt/android-sdk
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973" "cmake;3.22.1"
./gradlew testDebugUnitTest assembleRelease   # APK signé si le keystore est présent
```

La première compilation clone llama.cpp (accès réseau à github.com) et compile
les variantes CPU : comptez une dizaine de minutes de plus.

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
