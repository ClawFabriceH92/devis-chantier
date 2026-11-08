#!/usr/bin/env bash
# Reprise manuelle : paramétrer, créer un devis, générer le PDF (Waydroid).
set -uo pipefail
cd /root/projects/devis-chantier
UI="python3 scripts/ui.py"

echo "== réglages entreprise"
$UI tap "Réglages"; sleep 3
$UI form "Dénomination" "Plomberie Martin"
$UI form "Adresse" "1 rue des Artisans 92600 Asnieres"
$UI form "SIREN" "123456789"
$UI swipe-haut; sleep 1
$UI defiler "Assureur"
$UI form "Assureur" "AXA IARD"
$UI form "Coordonnées de l'assureur" "12 avenue de la Paix 75010 Paris"
$UI tap "Choisir la date d'expiration"; sleep 2
$UI defiler "Médiateur de la consommation"
$UI form "Médiateur de la consommation" "CM2C 14 rue Saint Jean 75010 Paris"
$UI capture final-reglages
$UI back; sleep 2

echo "== devis"
$UI tap "Nouveau devis"; sleep 3
$UI tap "Choisir un client"; sleep 2
$UI tap "Test Persistance"; sleep 2
$UI form "Objet du devis" "Renovation salle de bain"
$UI defiler "Lignes du devis"
$UI tap "Ajouter une ligne"; sleep 3
$UI tap "Rechercher un article"; sleep 1
adb shell input keyboard text "Mitigeur"; sleep 2
adb shell input keyevent 4; sleep 2
$UI tap "Mitigeur lavabo"; sleep 2
$UI tap "Valider la ligne"; sleep 3
$UI defiler "Total TTC"
$UI capture final-devis
$UI defiler "Enregistrer et prévisualiser"
$UI tap "Enregistrer et prévisualiser"; sleep 4
$UI defiler "Aperçu du PDF"
$UI capture final-detail
$UI tap "Aperçu du PDF"; sleep 6
$UI capture final-pdf
echo "== mentions manquantes ?"
$UI dump | grep -iE "mentions|manquante" | head -5 || echo "aucune mention manquante affichée"
echo "== traces PDF"
adb logcat -d | grep -iE "DevisChantier.*pdf|pdf généré" | tail -3
echo "== source de partage ouverte ?"
$UI dump | head -8
