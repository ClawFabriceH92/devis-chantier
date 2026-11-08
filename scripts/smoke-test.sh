#!/usr/bin/env bash
# QA visuelle de Devis Chantier sur l'émulateur de la CI.
#
# Le parcours va jusqu'au bout d'un devis : paramétrage de l'entreprise et de
# l'assurance, création d'un client, ligne prise dans le catalogue, contrôle des
# mentions, génération du PDF. Chaque étape vérifie un texte attendu et prend une
# capture : un écran vide ou un libellé disparu fait échouer le job, et les PNG
# restent en artefact.
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

remplir() {
  if ! $UI form "$1" "$2"; then
    echo "ÉCHEC : champ « $1 » non rempli"
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
$UI capture accueil-devis

echo "== 3. paramétrage de l'entreprise (roue dentée)"
taper "Réglages"
sleep 4
verifier "Mon entreprise"
remplir "Dénomination" "Plomberie Martin"
remplir "Adresse" "1 rue des Artisans 92600 Asnieres"
remplir "SIREN" "123456789"
$UI swipe-haut
$UI swipe-haut
voir "Assureur"
remplir "Assureur" "AXA IARD"
remplir "Coordonnées de l'assureur" "12 avenue de la Paix 75010 Paris"
taper "Choisir la date d'expiration"
sleep 2
verifier "Expiration de l'attestation"
$UI capture reglages-assurance
voir "Médiateur de la consommation"
remplir "Médiateur de la consommation" "CM2C 14 rue Saint Jean 75010 Paris"
voir "Intelligence artificielle"
verifier "Intelligence artificielle"
$UI capture reglages-ia
voir "Mise à jour"
verifier "Mise à jour"
$UI capture reglages-maj
$UI back
sleep 2

echo "== 4. catalogue d'articles et catalogue de départ"
taper "Articles"
sleep 3
verifier "Catalogue de départ"
$UI capture articles-vides
taper "Catalogue de départ"
sleep 3
taper "Ajouter le catalogue Plombier"
sleep 5
taper "Fermer"
sleep 3
voir "Mitigeur lavabo"
verifier "Mitigeur lavabo"
$UI capture articles-plombier

echo "== 5. carnet clients"
taper "Clients"
sleep 3
verifier "Rechercher dans mes clients"
verifier "Entreprise"
$UI capture clients
taper "Nouveau client"
sleep 2
remplir "Nom" "M. Durand"
taper "Enregistrer"
sleep 3
verifier "M. Durand"
$UI capture clients-rempli

echo "== 6. devis complet avec ligne du catalogue"
taper "Devis"
sleep 2
taper "Nouveau devis"
sleep 4
verifier "Client et objet"
taper "Choisir un client"
sleep 2
taper "M. Durand"
sleep 2
remplir "Objet du devis" "Renovation salle de bain"
voir "Lignes du devis"
taper "Ajouter une ligne"
sleep 3
verifier "Nouvelle ligne"
taper "Rechercher un article"
sleep 1
adb shell input text "Mitigeur"
sleep 2
adb shell input keyevent 4
sleep 2
taper "Mitigeur lavabo"
sleep 2
verifier "Mitigeur lavabo"
$UI capture ligne-catalogue
taper "Valider la ligne"
sleep 4
voir "Total TTC"
verifier "Total TTC"
$UI capture devis-totaux
voir "Enregistrer et prévisualiser"
taper "Enregistrer et prévisualiser"
sleep 4
voir "Actions"

echo "== 7. contrôle des mentions et génération du PDF"
voir "Aperçu du PDF"
taper "Aperçu du PDF"
sleep 5
$UI capture devis-apercu
if $UI dump | grep -qi "Mentions obligatoires manquantes"; then
  echo "ÉCHEC : mentions obligatoires encore incomplètes après paramétrage"
  $UI dump | head -40
  ECHECS=$((ECHECS + 1))
else
  echo "OK  aucune mention obligatoire manquante"
fi

echo "== 8. dictée (le micro doit être disponible)"
$UI back
sleep 3
voir "Nouveau devis vocal"
taper "Nouveau devis vocal"
sleep 4
verifier "Ce que vous dictez"
$UI capture dictee

echo "== contrôle des erreurs et plantages"
adb logcat -d > /tmp/smoke/logcat.txt || true
grep -i "pdf" /tmp/smoke/logcat.txt | head -5 || true
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
