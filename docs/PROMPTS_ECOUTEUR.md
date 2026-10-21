# Pack de prompts GPT Image 2 — L'Écouteur (0.17.0)

`screamer_listener.png` est aujourd'hui le seul visage **entièrement procédural**
de la banque : il est correct, mais il ne tient pas la comparaison avec les
quatorze planches IA à côté de lui. Ce pack sert à le remplacer, et accessoirement
à produire une planche de référence pour repeindre `listener.png` à la main.

La règle absolue de cette créature, celle qui doit survivre à toutes les
variantes : **elle n'a pas d'yeux.** Pas d'orbites creuses, pas d'iris pâle, pas
de regard. La peau a été tirée par-dessus et recousue. Si l'image générée
contient le moindre œil, elle est à jeter — c'est le contraire exact de tous les
autres monstres du mod, qui vous regardent.

---

## Bloc de style commun

À coller **au début de chaque prompt**. C'est ce bloc qui garantit que
L'Écouteur appartient visuellement à la même famille que l'Hôte, l'Absent et le
Contrefait.

> Photographie d'horreur analogique sombre, réaliste, grain de pellicule marqué,
> palette désaturée gris cireux à dominante froide légèrement turquoise, ombres
> écrasées, arrière-plan noir absolu, les bords de l'image se fondent dans le
> noir pur. Source de lumière **unique et dure, venant d'en bas** : le front
> reste dans l'obscurité, la mâchoire et le dessous du menton sont surexposés.
> Aucun texte, aucun filigrane, aucune signature. Image carrée.

## La créature — description à réutiliser telle quelle

> Le sujet : une créature humanoïde très haute et très maigre qui n'a **aucun
> œil**. À la place des yeux, deux fentes horizontales où la peau a été tirée
> puis **cousue avec du gros fil pâle**, les points de suture nettement visibles,
> la couture droite un peu plus basse que la gauche. Peau cireuse gris-beige
> marbrée, comme du vieux papier huilé, parcourue de fines coutures d'assemblage.
> De chaque côté du crâne, **deux pavillons d'oreille démesurés**, plats, larges
> comme le visage entier, ouverts vers l'avant comme des paraboles ; ils sont de
> tailles et d'inclinaisons différentes, nervurés de trois plis concentriques, et
> percés en leur centre d'un canal auditif noir. **Mâchoire décrochée** qui pend
> ouverte en permanence, quelques dents inférieures irrégulières. Lambeaux d'un
> tissu turquoise délavé autour du cou.

**À interdire explicitement en fin de prompt** (les modèles ont une pulsion
irrépressible à rajouter des yeux) :

> Interdit : yeux, iris, pupilles, orbites, globes oculaires, lueur dans le
> visage, oreilles animales pointues ou poilues, oreilles d'elfe, cornes,
> masque, casque, texte.

---

## 15 → `screamer_listener.png` — le prompt principal

C'est celui qui remplace le fichier actuel. Cadrage pensé pour le rendu
« cover » de `JumpscareManager` : tout ce qui compte est dans les 60 % centraux,
les pavillons peuvent sortir du cadre sans dommage.

> \[bloc de style commun] \[description de la créature]
>
> Gros plan très serré, le visage remplit tout le cadre et déborde. La tête est
> légèrement décentrée vers la gauche et penchée de 10 degrés. Les deux immenses
> pavillons d'oreille sont **tendus vers l'avant, vers l'objectif**, et sortent
> des bords gauche et droit de l'image. La mâchoire est grande ouverte, très bas,
> l'intérieur de la bouche noir absolu. Les deux coutures qui remplacent les yeux
> sont nettement lisibles, fil pâle sur peau grise. Rien dans ce visage ne
> regarde l'objectif — la créature est **orientée vers un son hors champ**, pas
> vers le spectateur. Léger flou de mouvement vertical, comme prise en pleine
> réaction.
>
> \[interdictions]

---

## Variantes (facultatif — pour une future rotation)

Si tu veux plus tard des visages dédiés à L'Écouteur dans la rotation générale,
garde le même bloc de style et change **uniquement le cadrage et la lumière** :
la variété doit venir du plan, jamais d'un monstre différent.

### A — « L'oreille »
> Cadrage serré à l'horizontale sur **un seul pavillon d'oreille**, de profil aux
> trois quarts, occupant les deux tiers de l'image. Les nervures concentriques et
> le canal auditif noir en son centre sont l'unique sujet. Le visage n'apparaît
> que comme une masse sombre floue en bord de cadre. Éclairage rasant qui accroche
> les plis du cartilage.

### B — « Elle écoute »
> Plan large sur le buste, la créature debout et **parfaitement immobile**, tête
> tournée à 60 degrés sur le côté, loin de l'objectif, un pavillon offert au
> spectateur. Bras anormalement longs qui pendent inertes jusqu'aux genoux, mains
> à trois doigts. Posture de statue. Aucune tension musculaire nulle part.

### C — « Les coutures »
> Cadrage macro sur la moitié supérieure du visage seulement, du haut du nez au
> front. Les deux sutures qui ferment les orbites occupent toute la largeur de
> l'image : fil, nœuds, peau tendue et légèrement froncée autour de chaque point.
> Netteté extrême, profondeur de champ très courte.

### D — « Elle a entendu »
> Le visage rejeté en arrière, menton pointé vers le haut, pavillons grands
> ouverts vers l'avant, bouche béante au maximum. Vu en légère contre-plongée.
> C'est l'instant du verrouillage : la seule image du pack où la créature est en
> mouvement.

---

## Planche de référence pour la texture 64×64

Format **différent** des screamers : ici on veut une planche lisible, pas une
photo. Elle sert de modèle pour repeindre `listener.png` à la main (le script
`tools/gen_listener_assets.py` documente la carte UV exacte dans
`docs/ASSETS.md`).

> Planche de référence de personnage, fond gris neutre uni, éclairage d'atelier
> uniforme et frontal, style peinture numérique nette et lisible. Trois vues
> alignées de la même créature en pied : **de face, de profil, de dos**.
> Humanoïde de 2,9 mètres, extrêmement maigre, jambes en deux segments avec le
> **genou inversé vers l'arrière**, bras trop longs dont les mains à trois doigts
> arrivent aux genoux, ongles pâles. Aucun œil : deux coutures horizontales
> cousues à la place, la droite plus basse. Deux immenses pavillons d'oreille
> plats et nervurés de part et d'autre du crâne. Mâchoire décrochée pendante.
> Peau cireuse gris-beige marbrée, coutures d'assemblage visibles au sternum et
> sur le crâne, vestige d'une tunique turquoise délavée déchirée en diagonale sur
> le torse. Épaule gauche plus haute que la droite. Pas de texte, pas de flèches,
> pas d'annotations.

---

## Installation

1. Génère en **1:1, 1024×1024 minimum**, PNG.
2. Nomme le fichier `screamer_listener.png` (ou `15.png` si tu régénères tout le
   pack dans l'ordre de `docs/PROMPTS_SCREAMERS.md` — la quinzième cible a été
   ajoutée à `tools/import_screamers.py`).
3. Place-le dans un dossier, puis :
   ```
   python3 tools/import_screamers.py <dossier>
   ```
   Le script recadre au carré, redimensionne en 1024×1024, fond les bords au noir
   pur et installe le fichier au bon endroit ; l'ancien est sauvegardé dans
   `build/backup_screamers/`.
4. `./gradlew check` revalide la dimension exacte (1024×1024) via
   `validatePenumbraResources`.

## Critères d'acceptation

Une planche est bonne si, en la regardant une demi-seconde :

- **aucun œil** nulle part, et les coutures sont lisibles comme des coutures ;
- les oreilles se lisent immédiatement comme des **oreilles**, pas comme des
  ailes, des cornes ou une coiffe ;
- la lumière vient bien **du bas** — front noir, mâchoire brûlée ;
- la créature **ne regarde pas** le spectateur ;
- posée à côté de `screamer_host.png` et `screamer_absent.png`, elle a le même
  grain, le même contraste et la même température de couleur.
