#!/usr/bin/env bash
# QA visuelle de Devis Chantier sur l'émulateur de la CI.
# Chaque étape vérifie un texte attendu et prend une capture : un écran vide ou
# un libellé disparu fait échouer le job, et les PNG restent en artefact.
set -uo pipefail

PKG=com.trucdecomptable.devischantier
APK=app/build/outputs/apk/debug/app-debug.apk
UI="python3 scripts/ui.py"
ECHECS=0

verifier() {
  if ! $UI expect "$1"; then
    echo "ÉCHEC : écran inattendu, « $1 » attendu"
    $UI dump || true
    ECHECS=$((ECHECS + 1))
  fi
}

taper() {
  if ! $UI tap "$1"; then
    echo "ÉCHEC : impossible de taper « $1 »"
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

echo "== 1. assistant de premier lancement"
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
$UI capture onboarding-tva
taper "Continuer"
sleep 2
verifier "Qui établit la facture"
$UI capture onboarding-comptable
taper "Terminer"
sleep 4

echo "== 2. accueil des devis"
verifier "Nouveau devis"
$UI capture accueil-devis

echo "== 3. création d'un devis"
taper "Nouveau devis"
sleep 4
verifier "Client et objet"
verifier "Lignes du devis"
$UI capture devis-edition
taper "Ajouter une ligne"
sleep 3
verifier "Nouvelle ligne"
verifier "Choisir dans le catalogue"
$UI capture devis-ligne
taper "Annuler"
sleep 2

echo "== 4. catalogue d'articles et catalogue de départ"
taper "Articles"
sleep 3
verifier "Catalogue de départ"
$UI capture articles
taper "Catalogue de départ"
sleep 3
taper "Ajouter le catalogue Plombier"
sleep 4
$UI tap "Fermer"
sleep 3
verifier "Mitigeur lavabo"
$UI capture articles-plombier

echo "== 5. carnet clients et recherche d'entreprise"
taper "Clients"
sleep 3
verifier "Rechercher dans mes clients"
verifier "Entreprise"
$UI capture clients

echo "== 6. réglages, IA et sauvegarde"
taper "Devis"
sleep 2
taper "Réglages"
sleep 4
verifier "Mon entreprise"
verifier "Assurance professionnelle"
$UI capture reglages-haut
$UI swipe-haut
$UI swipe-haut
$UI swipe-haut
verifier "Intelligence artificielle"
$UI capture reglages-ia
$UI swipe-haut
$UI swipe-haut
$UI swipe-haut
verifier "Sauvegarde"
$UI capture reglages-sauvegarde
$UI swipe-haut
$UI swipe-haut
$UI swipe-haut
verifier "Mise à jour"
$UI capture reglages-maj

echo "== 7. dictée (le micro doit être disponible)"
$UI back
sleep 2
taper "Nouveau devis vocal"
sleep 3
verifier "Ce que vous dictez"
$UI capture dictee

echo "== contrôle des erreurs et plantages"
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
