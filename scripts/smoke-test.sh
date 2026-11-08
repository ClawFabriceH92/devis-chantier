#!/usr/bin/env bash
# QA visuelle de Devis Chantier sur l'émulateur de la CI.
#
# Ce script prouve ce qu'un test JVM ne peut pas : l'application s'installe,
# démarre, et chaque écran se rend avec ses libellés. Les vérifications
# métier qui exigent un vrai Android (PDF, sauvegarde chiffrée, clé API, Room)
# sont couvertes juste avant par les 7 tests instrumentés
# (`./gradlew connectedDebugAndroidTest`).
#
# La saisie de texte au clavier n'est volontairement pas automatisée ici :
# `adb shell input` n'arrive pas de façon fiable dans les champs Compose d'un
# émulateur, et un faux échec de saisie masquerait les vrais défauts d'écran.
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
verifier "Nouveau devis vocal"
verifier "Articles"
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
taper "Fermer"
sleep 3
voir "Mitigeur lavabo"
verifier "Mitigeur lavabo"
$UI capture articles-plombier
# Un article s'ouvre, se modifie et se referme.
taper "Modifier"
sleep 2
verifier "Fiche article"
verifier "Synonymes pour la dictée"
$UI capture article-fiche
taper "Annuler"
sleep 2

echo "== 4. carnet clients et recherche d'entreprise"
taper "Clients"
sleep 3
verifier "Rechercher dans mes clients"
verifier "Annuaire des Entreprises"
$UI capture clients
taper "Rechercher mon entreprise"
sleep 1
# Requête vide : l'application doit répondre sans planter.
sleep 4
$UI capture clients-annuaire

echo "== 5. réglages : entreprise, assurance, IA, sauvegarde, mise à jour"
taper "Devis"
sleep 2
taper "Réglages"
sleep 4
verifier "Mon entreprise"
verifier "Rechercher mon entreprise"
$UI capture reglages-entreprise
voir "Assureur"
verifier "Assureur"
verifier "Choisir la date d'expiration"
verifier "Rappel avant expiration"
$UI capture reglages-assurance
voir "Intelligence artificielle"
verifier "Tester la dictée assistée"
verifier "Qualité maximale"
$UI capture reglages-ia
voir "Sauvegarde"
verifier "Sauvegarde automatique"
verifier "Restaurer une sauvegarde"
$UI capture reglages-sauvegarde
voir "Mise à jour"
verifier "Vérifier maintenant"
$UI capture reglages-maj
$UI back
sleep 2

echo "== 6. devis : écran de saisie et ligne"
taper "Nouveau devis"
sleep 4
verifier "Client et objet"
verifier "Objet du devis"
verifier "Validité (jours)"
$UI capture devis-edition
voir "Lignes du devis"
taper "Ajouter une ligne"
sleep 3
verifier "Nouvelle ligne"
verifier "Choisir dans le catalogue"
$UI capture devis-ligne
taper "Valider la ligne"
sleep 4
voir "Total TTC"
verifier "Total TTC"
verifier "Total HT"
$UI capture devis-totaux
voir "Enregistrer et prévisualiser"
taper "Enregistrer et prévisualiser"
sleep 4
voir "Aperçu du PDF"
verifier "Envoyer au client"
verifier "Transmettre au comptable"
$UI capture devis-detail

echo "== 7. dictée"
$UI back
sleep 3
voir "Nouveau devis vocal"
taper "Nouveau devis vocal"
sleep 4
verifier "Ce que vous dictez"
verifier "Parler"
$UI capture dictee

echo "== 8. marges et achats"
$UI back
sleep 2
taper "Marges"
sleep 3
verifier "Tableau de bord"
verifier "Ajouter un achat"
$UI capture marges

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
