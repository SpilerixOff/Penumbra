# Penumbra 0.26.0 — Gestes des créatures

## Recherche et choix de conception

Il n'existe pas de classement universel des gestes qui font le plus peur.
Les travaux ci-dessous donnent des pistes pour le rythme, la proximité et
l'étrangeté ; ils ne prouvent pas l'efficacité de ces animations dans Penumbra.

- [Thieu et al., *Visual looming is a primitive for human emotion*](https://pmc.ncbi.nlm.nih.gov/articles/PMC10491236/) :
  ce préprint étudie l'expansion visuelle d'objets approchants et des réponses
  défensives. Dans les vidéos naturelles, l'association porte surtout sur
  l'activation ressentie, pas exclusivement sur la peur. Choix : faire avancer
  le corps dans le monde, avec une accélération lisible.
- [Grillon et al., 2004](https://pubmed.ncbi.nlm.nih.gov/15506874/) :
  l'imprévisibilité augmente le sursaut dans certaines conditions aversives,
  pas dans toutes. Choix : conserver des attentes et des issues différentes,
  avec une préparation perceptible une fois l'attaque engagée.
- [Saygin et al., 2012](https://pubmed.ncbi.nlm.nih.gov/21515639/) :
  le décalage entre apparence humaine et mouvement mécanique modifie les
  réponses liées à la perception des actions. Cela ne démontre pas que chaque
  mouvement anormal provoque la peur. Choix : retarder certains membres et
  rompre une pose familière, surtout chez les imitateurs.
- [Chun et al., 2020](https://pubmed.ncbi.nlm.nih.gov/33192815/) :
  trois visionnages d'un film à suspense réduisent plusieurs mesures de tension,
  sans baisse significative du plaisir déclaré. Choix : donner des signatures
  différentes aux monstres plutôt que reproduire huit fois la même charge.

Les durées, angles, poses et règles ci-dessous sont des décisions de jeu.
La réussite d'un test technique ne mesure pas la peur ressentie par un joueur.

## Signatures

| Créature | Préparation et action | Règle conservée |
|---|---|---|
| L'Hôte | Bond, plongée, prise à deux mains, levée et projection. | Seul monstre à effectuer ce porté ; version 0.25.0 conservée. |
| L'Observateur | Statue repliée, torse vrillé, tête gardée vers le joueur ; fente et frappe oblique d'un seul bras, puis retour raide. | Charge engagée vers une position, esquivable ; obstacles et flash interrompent. |
| Le Rampant | Pattes avant décrochées successivement, dépliage en chute ; crochet bas, compression et rebond avant retour à la chasse. | Prédateur de plafond/paroi ; sortie de zone, lumière et caméra restent utiles. |
| L'Écouteur | Pavillons puis tête orientés vers le bruit, torse en retard ; long balayage d'un bras et retrait. | Bruit, visibilité et portée sont revérifiés au moment du contact. |
| La Cavaleuse | Vague de huit pattes, freinage, pattes avant dressées, claquement au sol puis repli diagonal. | Une feinte par poursuite ; aucun dégât ajouté. |
| Le Contrefait | Compression de pantin, bassin et épaule désaccordés ; l'épaule entraîne un crochet asymétrique, puis le corps se replie. | Immobilité lorsqu'observé ; contact réel après charge engagée, bloquée par les murs. |
| Le Projectionniste | Armement de la machine, lentille et bobines, déploiement du manteau pendant l'avancée, repli. | Manifestation photographique sans dégâts. |
| L'Originel | Silhouette presque rigide, longue bascule et ouverture tardive des bras, redressement. | Apparition distante conservée ; attaque de grotte engagée, contact réel et obstacles. |
| L'Absent | Imitation préservée, puis désaccord tête/torse ; torsion et bras croisé pendant son bond, suite de geste après contact. | Faux compagnon, identité copiée et confrontation finale conservent leurs branches propres. |

Les horloges d'attaque viennent du serveur. Les oscillations de marche peuvent
rester locales ; elles ne décident jamais d'un dégât. Les squelettes relient
les épaules et la tête au torse quand le format le permet. Le modèle humanoïde
de l'Absent conserve les pivots nécessaires aux objets, vêtements et armures.

Voir les [résultats et limites](KNOWN_ISSUES.md) pour les essais réellement
effectués. Installer la même version 0.26.0 sur le serveur et chaque client.
