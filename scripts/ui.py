#!/usr/bin/env python3
"""Petit pilote d'émulateur pour la QA visuelle de Devis Chantier.

    python3 scripts/ui.py dump                 # affiche les textes visibles
    python3 scripts/ui.py tap "Nouveau devis"  # tape le centre du nœud cliquable
    python3 scripts/ui.py expect "Articles"    # code 1 si le texte est absent
    python3 scripts/ui.py capture accueil       # /tmp/smoke/NN-accueil.png
    python3 scripts/ui.py swipe-haut           # fait défiler la page

Le tap passe TOUJOURS par uiautomator (bornes réelles), jamais par des
coordonnées devinées : le nœud retenu est celui de plus petite surface, et son
ancêtre cliquable si le libellé lui-même ne l'est pas.
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

SORTIE = "/tmp/smoke"


def adb(*args, capture=True):
    return subprocess.run(
        ["adb", *args], check=True, capture_output=capture, text=True
    ).stdout


def dump_xml() -> ET.Element:
    os.makedirs(SORTIE, exist_ok=True)
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", f"{SORTIE}/ui.xml")
    with open(f"{SORTIE}/ui.xml", encoding="utf-8") as fichier:
        return ET.fromstring(fichier.read())


def bornes(noeud):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", noeud.get("bounds", ""))
    if not match:
        return None
    x1, y1, x2, y2 = (int(v) for v in match.groups())
    return x1, y1, x2, y2


def surface(noeud):
    b = bornes(noeud)
    if not b:
        return 1 << 30
    return max(1, (b[2] - b[0]) * (b[3] - b[1]))


def tous_les_textes(racine):
    textes = []
    for noeud in racine.iter("node"):
        for attribut in ("text", "content-desc"):
            valeur = (noeud.get(attribut) or "").strip()
            if valeur:
                textes.append(valeur)
    return textes


def trouver(racine, recherche):
    """Nœuds dont le texte ou la description contient la recherche."""
    candidats = []
    for noeud in racine.iter("node"):
        for attribut in ("text", "content-desc"):
            valeur = (noeud.get(attribut) or "").strip()
            if valeur and (recherche.lower() in valeur.lower()):
                candidats.append((noeud, valeur == recherche))
    if not candidats:
        return None
    # correspondance exacte d'abord, puis la plus petite surface
    exacts = [c for c in candidats if c[1]]
    pool = exacts or candidats
    pool.sort(key=lambda c: surface(c[0]))
    return pool[0][0]


def ancetre_cliquable(noeud, parents):
    courant = noeud
    while courant is not None:
        if courant.get("clickable") == "true":
            return courant
        courant = parents.get(id(courant))
    return noeud


def construire_parents(racine):
    parents = {}
    for parent in racine.iter("node"):
        for enfant in parent:
            parents[id(enfant)] = parent
    return parents


def cmd_dump():
    racine = dump_xml()
    for texte in tous_les_textes(racine):
        print(texte)


def cmd_expect(attendu):
    racine = dump_xml()
    textes = tous_les_textes(racine)
    if any(attendu.lower() in t.lower() for t in textes):
        print(f"OK  écran contient « {attendu} »")
        return 0
    print(f"KO  « {attendu} » absent. Textes visibles :")
    for texte in textes:
        print("   -", texte)
    return 1


def cmd_tap(recherche, attente=1.5):
    racine = dump_xml()
    parents = construire_parents(racine)
    noeud = trouver(racine, recherche)
    if noeud is None:
        print(f"KO  aucun nœud ne contient « {recherche} »")
        for texte in tous_les_textes(racine)[:40]:
            print("   -", texte)
        return 1
    cible = ancetre_cliquable(noeud, parents)
    b = bornes(cible) or bornes(noeud)
    x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    print(f"tap « {recherche} » -> {x},{y}")
    subprocess.run(["sleep", str(attente)], check=False)
    return 0


def cmd_capture(nom):
    os.makedirs(SORTIE, exist_ok=True)
    existants = sorted(f for f in os.listdir(SORTIE) if f.endswith(".png"))
    chemin = f"{SORTIE}/{len(existants) + 1:02d}-{nom}.png"
    with open(chemin, "wb") as fichier:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=fichier)
    print("capture", chemin)
    return 0


def cmd_swipe_haut():
    taille = adb("shell", "wm", "size").strip()
    match = re.search(r"(\d+)x(\d+)", taille)
    if not match:
        match = None
        largeur, hauteur = 1080, 2400
    else:
        largeur, hauteur = int(match.group(1)), int(match.group(2))
    adb(
        "shell", "input", "swipe",
        str(largeur // 2), str(int(hauteur * 0.75)),
        str(largeur // 2), str(int(hauteur * 0.25)), "300",
    )
    subprocess.run(["sleep", "1"], check=False)
    print("swipe vers le haut")
    return 0


def cmd_form(etiquette, valeur):
    """Remplit le champ de saisie associé à un libellé, en vérifiant le résultat.

    Les bornes bougent quand le libellé « flotte » : on relit le dump juste
    avant de taper, et on recommence une fois si la valeur n'est pas arrivée.
    """
    for tentative in range(1, 4):
        racine = dump_xml()
        champs = [n for n in racine.iter("node") if (n.get("class") or "").endswith("EditText")]
        libelles = [
            n for n in racine.iter("node")
            if (n.get("text") or "").strip() == etiquette
        ]
        if not libelles or not champs:
            print(f"KO  champ « {etiquette} » introuvable (essai {tentative})")
            return 1
        lab = bornes(libelles[0])
        centre = (lab[1] + lab[3]) // 2
        cible = min(champs, key=lambda n: abs(((bornes(n)[1] + bornes(n)[3]) // 2) - centre))
        b = bornes(cible)
        x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
        adb("shell", "input", "tap", str(x), str(y))
        subprocess.run(["sleep", "1"], check=False)
        adb("shell", "input", "keyevent", "123")
        for _ in range(len((cible.get("text") or "")) + 6):
            adb("shell", "input", "keyevent", "67")
        # `input keyboard text` : la variante `input text` n'arrive pas dans les
        # champs Compose quand l'IME est déjà instancié (vécu 08/11/2026).
        adb("shell", "input", "keyboard", "text", valeur.replace(" ", "%s"))
        subprocess.run(["sleep", "1"], check=False)
        adb("shell", "input", "keyevent", "4")
        subprocess.run(["sleep", "1"], check=False)
        apres = dump_xml()
        textes = [n.get("text") or "" for n in apres.iter("node")]
        attendu = valeur.replace("%s", " ")
        if any(attendu.lower() in t.lower() for t in textes):
            print(f"OK  « {etiquette} » = {attendu}")
            return 0
        print(f"    valeur non reçue (essai {tentative}), nouvelle tentative")
    print(f"KO  impossible de remplir « {etiquette} »")
    return 1


def cmd_defiler_vers(texte, max_essais=8):
    """Fait défiler jusqu'à ce que le texte soit visible (listes et formulaires longs)."""
    for essai in range(1, max_essais + 1):
        racine = dump_xml()
        if trouver(racine, texte) is not None:
            print(f"OK  « {texte} » visible après {essai - 1} défilement(s)")
            return 0
        cmd_swipe_haut()
    print(f"KO  « {texte} » reste introuvable après défilement")
    return 1


def cmd_back():
    adb("shell", "input", "keyevent", "4")
    subprocess.run(["sleep", "1"], check=False)
    return 0


def principal():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    commande = sys.argv[1]
    if commande == "dump":
        return cmd_dump()
    if commande == "expect":
        return cmd_expect(sys.argv[2])
    if commande == "tap":
        return cmd_tap(sys.argv[2])
    if commande == "capture":
        return cmd_capture(sys.argv[2])
    if commande == "swipe-haut":
        return cmd_swipe_haut()
    if commande == "back":
        return cmd_back()
    if commande == "form":
        return cmd_form(sys.argv[2], sys.argv[3])
    if commande == "defiler":
        return cmd_defiler_vers(sys.argv[2])
    print(f"commande inconnue : {commande}")
    return 2


if __name__ == "__main__":
    sys.exit(principal())
