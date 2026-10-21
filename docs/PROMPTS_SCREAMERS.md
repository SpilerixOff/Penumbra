# Pack de prompts GPT Image 2 — visages de screamers (0.14.0)

Le problème des screamers actuels : 9 des 14 visages sont des variantes du même
démon brûlé aux yeux orange, sans lien avec l'identité du mod. Ici, chaque visage
est un **plan différent de la même créature** — l'Hôte, la copie imparfaite —
comme 14 photogrammes volés d'un même film. La variété vient du cadrage et de la
lumière, jamais d'un monstre différent : c'est ce qui rend la rotation cohérente
ET imprévisible.

## Mode d'emploi

1. Copie le **bloc de style commun** ci-dessous au début de CHAQUE prompt.
2. Génère en **1:1 (carré), 1024×1024 minimum**, format PNG.
3. Nomme les fichiers `01.png` à `14.png` (ou directement du nom cible) dans un
   dossier, puis lance :
   `python3 tools/import_screamers.py <dossier>`
   Le script recadre, redimensionne, fond les bords au noir pur et installe les
   fichiers au bon endroit (les anciens sont sauvegardés).

## Bloc de style commun (à coller avant chaque prompt)

> Photographie d'horreur analogique sombre, réaliste, grain de pellicule léger,
> palette désaturée gris-turquoise, ombres écrasées, arrière-plan noir absolu,
> les bords de l'image se fondent dans le noir pur. Le sujet : une créature
> humanoïde pâle qui imite maladroitement un être humain — peau cireuse
> gris-beige comme du vieux papier, fines coutures d'assemblage sur le visage
> (comme un costume de peau mal cousu), orbites creuses très sombres avec un
> minuscule point d'iris pâle, un œil légèrement plus bas que l'autre, bouche
> fine trop large qui tombe d'un côté, lambeaux d'un tissu turquoise délavé
> autour du cou. Aucun texte, aucun filigrane, aucune signature. Image carrée.

## Les 14 prompts

### 01 → `screamer_host.png` — Le face-à-face
Gros plan frontal du visage entier, parfaitement centré, qui fixe l'objectif.
Expression neutre, figée, à peine trop calme. Éclairage faible venant du bas.
La bouche fermée. C'est le plan « de référence », le plus simple et le plus lisible.

### 02 → `screamer_host_2.png` — Trop près
Le visage à deux centimètres de l'objectif, cadré du front au menton, légèrement
flou par la proximité, narines et coutures de peau très visibles. Un seul œil
entier dans le cadre, l'autre coupé par le bord.

### 03 → `screamer_host_3.png` — Le cri
Bouche grande ouverte, trop grande ouverte — la mâchoire descend plus bas qu'un
humain ne le permet. Intérieur de la bouche noir absolu, sans dents visibles.
Tête légèrement rejetée en arrière, très léger flou de mouvement vertical.

### 04 → `screamer_host_4.png` — De biais
Le visage penché à 20 degrés, décalé dans le tiers droit de l'image, moitié
gauche mangée par l'obscurité. L'œil visible est écarquillé. Comme surpris
en train d'observer.

### 05 → `screamer_host_5.png` — Les yeux seulement
Cadrage serré horizontal sur les deux orbites uniquement, du haut des pommettes
aux sourcils. Les deux points d'iris pâles ne regardent pas dans la même
direction. Tout le reste est noir.

### 06 → `screamer_host_6.png` — Le profil qui se retourne
Profil gauche en train de pivoter brusquement vers l'objectif, fort flou de
rotation sur l'arrière du crâne, l'œil déjà fixé sur la caméra alors que la
tête n'a pas fini de tourner.

### 07 → `screamer_host_7.png` — Derrière la vitre
Le visage pressé contre une vitre invisible : nez et joue écrasés, buée légère,
une paume aux doigts trop longs posée à plat à côté du visage.

### 08 → `screamer_host_8.png` — La rémanence
Double exposition : le même visage deux fois, décalé de quelques centimètres,
l'une des deux images plus pâle et plus grande, comme une image fantôme de
télévision analogique. Léger déchirement horizontal de balayage vidéo.

### 09 → `screamer_host_9.png` — Presque rien
Le visage à 90 % dans le noir : seuls une pommette, l'arête du nez et un œil
accrochent une lumière très faible venue de côté. Il faut une seconde pour
comprendre qu'un visage est là. Le plus discret des 14.

### 10 → `screamer_rampant.png` — Le Rampant
La même créature mais à quatre pattes, vue de face au ras du sol, tête humaine
relevée vers l'objectif à un angle impossible, échine saillante visible derrière,
bras trop longs pliés comme des pattes avant. Sol de pierre sombre.

### 11 → `screamer_absent.png` — L'Absent
Un visage de joueur Minecraft — carré, pixelisé — mais rendu comme un masque
de peau réelle tendu sur un crâne : la frontière entre les gros pixels et la
peau cireuse est cousue de fil sombre. Les yeux carrés sont vides et noirs.

### 12 → `screamer_chorus.png` — Le chœur
Cinq exemplaires du même visage pâle à différentes profondeurs dans le noir,
du premier plan net au fond à peine visible, tous exactement avec la même
expression, tous fixant l'objectif. Aucun ne se touche.

### 13 → `screamer_refuge.png` — Dans l'embrasure
Une porte en bois de maison Minecraft entrouverte dans la pénombre ; le visage
pâle occupe l'ouverture verticale, coupé par le chambranle, un œil et une main
aux doigts trop longs enroulée autour du bord de la porte.

### 14 → `screamer_mimic.png` — Le miroir raté
Un visage coupé verticalement en deux moitiés : à gauche, un visage humain
normal et chaud ; à droite, la copie cireuse et cousue qui tente la même
expression et échoue — le sourire tombe, l'œil est 1 cm trop bas. La couture
passe exactement au milieu.

## Conseils

- Si un rendu sort trop clair : ajoute « image très sombre, exposition -2 » au prompt.
- Si la créature sort trop « zombie » : ajoute « pas de blessures, pas de sang,
  peau intacte mais fausse » (l'horreur vient de l'imitation ratée, pas du gore).
- Le style 09 (« Presque rien ») est le plus important pour l'anti-habituation :
  n'hésite pas à le générer plusieurs fois pour garder le plus subtil.
- Les visages passent en plein écran ~0,4 s : la lisibilité de la silhouette
  compte plus que les détails fins.
