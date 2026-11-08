#!/usr/bin/env bash
# QA visuelle de Devis Chantier sur l'émulateur de la CI.
#
# Ce script prouve ce qu'un test JVM ne peut pas : l'application s'installe,
# démarre, et chaque écran principal se rend avec ses libellés et ses données.
# La logique métier qui exige un vrai Android (PDF, sauvegarde chiffrée, clé API,
# base Room, verrouillage après envoi) est couverte juste avant par les 7 tests
# instrumentés lancés par le workflow (`connectedDebugAndroidTest`).
#
# Deux principes, appris à la dure :
#   - pas de saisie de texte : `adb shell input` n'arrive pas de façon fiable
#     dans les champs Compose d'un émulateur, et un faux échec de saisie masque
#     les vrais défauts d'écran ;
#   - pas de navigation par le bouton retour ni par les dialogues imbriqués :
#     la position de défilement diffère d'un appareil à l'autre.
set -uo pipefail

PKG=com.trucdecomptable.devischantier
APK=app/build/outputs/apk/debug/app-debug.apk
UI="python3 scripts/ui.py"
ECHECS=0

verifier() {
  if ! $UI expect "$1"; then
    echo "ÉCHEC : écran inattendu, « $1 » attendu"
    ECHECS=$((ECHECS + 1))
  fi
}

taper() {
  if ! $UI tap "$1"; then
    echo "ÉCHEC : impossible de taper « $1 »"
    ECHECS=$((ECHECS + 1))
  fi
}

voir() {
  if ! $UI defiler "$1"; then
    echo "ÉCHEC : « $1 » introuvable après défilement"
    ECHECS=$((ECHECS + 1))
  fi
}

echo "== installation"
adb install -r -g "$APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell settings put global window_animation_scale 0 || true
adb logcat -c || true

echo "== démarrage"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 8
adb shell pidof "$PKG" || { echo "ÉCHEC : l'application n'est pas lancée"; exit 1; }

echo "== 1. assistant de premier lancement (4 étapes)"
verifier "Votre métier"
$UI capture onboarding-metier
taper "Plombier"
taper "Continuer"
sleep 3
verifier "Votre entreprise"
$UI capture onboarding-entreprise
taper "Continuer"
sleep 2
verifier "TVA et assurance"
verifier "Assureur"
$UI capture onboarding-tva
taper "Continuer"
sleep 2
verifier "Qui établit la facture"
verifier "Email du comptable ou de l'assistant"
$UI capture onboarding-comptable
taper "Terminer"
sleep 4

echo "== 2. accueil des devis"
verifier "Nouveau devis"
verifier "Nouveau devis vocal"
verifier "Brouillons"
$UI capture accueil-devis

echo "== 3. catalogue de départ par métier"
taper "Articles"
sleep 3
verifier "Catalogue de départ"
taper "Catalogue de départ"
sleep 3
verifier "Ajouter le catalogue Plombier"
taper "Ajouter le catalogue Plombier"
sleep 5
voir "Mitigeur lavabo"
verifier "Mitigeur lavabo"
verifier "85,00 €"
$UI capture articles-plombier

echo "== 4. carnet clients"
taper "Clients"
sleep 3
verifier "Rechercher dans mes clients"
verifier "Annuaire des Entreprises"
$UI capture clients

echo "== 5. devis : écran de saisie"
taper "Devis"
sleep 2
taper "Nouveau devis"
sleep 4
verifier "Client et objet"
verifier "Objet du devis"
verifier "Validité (jours)"
voir "Lignes du devis"
verifier "Ajouter une ligne"
$UI capture devis-edition

echo "== 6. contrôle des erreurs et plantages"
adb logcat -d > /tmp/smoke/logcat.txt || true
if grep -q "FATAL EXCEPTION" /tmp/smoke/logcat.txt; then
  echo "ÉCHEC : plantage détecté"
  grep -A 25 "FATAL EXCEPTION" /tmp/smoke/logcat.txt | head -60
  ECHECS=$((ECHECS + 1))
fi
if grep -E "^E/.*($PKG|AndroidRuntime)" /tmp/smoke/logcat.txt | grep -v "No package identifier" > /tmp/smoke/erreurs.txt; then
  echo "-- erreurs applicatives (non bloquantes) --"
  head -20 /tmp/smoke/erreurs.txt
fi

if [ "$ECHECS" -gt 0 ]; then
  echo "QA émulateur : $ECHECS échec(s)"
  exit 1
fi
echo "QA émulateur : toutes les étapes sont passées"
