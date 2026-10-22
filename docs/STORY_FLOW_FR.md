# Le parcours d'un joueur — Penumbra 0.23.0

Penumbra raconte une enquête personnelle dans un monde de survie partagé.
Le serveur ne passe pas une fois pour tous du début à la fin : chaque joueur
garde ses découvertes, son chapitre, ses pages et sa victoire. Un nouvel
arrivant commence donc sa propre histoire, même si quelqu'un a déjà terminé.

## Ce que voit un nouveau joueur

À sa première connexion avec le journal activé, il reçoit **Le Relevé** et un
message expliquant le clic droit pour le lire. Rien ne s'ouvre de force.
S'il n'a aucune place, la remise attend qu'il en libère une. Une reconnexion
ne distribue pas un nouveau carnet. En cas de perte, **un livre et une poche
d'encre** permettent d'en fabriquer un autre, sans perdre ses découvertes.

Le début laisse normalement deux minutes de grâce avant les événements du
directeur ; ce délai est configurable. Il construit un refuge, utilise un
lit et explore. L'ordre précis des manifestations varie : il n'y a pas une
cinématique identique ni une durée garantie pour toutes les parties.

Pour que le jeu reconnaisse ce refuge, prendre **deux pauses immobiles
distinctes d'environ dix secondes au même endroit**, de nuit ou sous une
faible lumière, en bougeant entre les deux. Poser un lit ne suffit pas à lui
seul : ces pauses ancrent le lieu, puis les repos et les rencontres près du
refuge font avancer l'enquête. Les premiers feuillets rappellent cette action.

Les premiers feuillets du Relevé montrent **la piste actuelle de ce joueur**.
Ils se mettent à jour lorsqu'il rouvre le carnet. Les autres pages mêlent
le témoignage d'Elias Warrick, les créatures réellement observées et les
méthodes retrouvées. Le journal incomplet est un autre objet : il raconte
des observations sur les habitudes du joueur, il ne remplace pas Le Relevé.

## Les écrits et les lieux

| Lieu | Page du Relevé | Utilité |
|---|---|---|
| Poste d'observation | Le flash | Comprendre le cadrage et la caméra |
| Maison décalée | Les refuges | Reconnaître les imitations de son abri |
| Sanctuaire de cendre | L'immobilité | Comprendre comment réagir à L'Écouteur |
| Puits de pénombre | La dernière page | Comprendre le sort et l'hypothèse de Warrick |

Fouiller le coffre associé au site permet à chacun de relever sa page, même
si le butin a déjà été pris. Cela ne régénère pas les objets du coffre. Les
pages physiques trouvées restent lisibles et la connaissance reste propre
à chacun. Le coffre doit encore exister à son emplacement enregistré : un
site détruit par les joueurs n'est pas reconstruit automatiquement.

La boussole d'arpenteur aide à trouver le camp ou des sites déjà générés ;
elle ne garantit pas un nouveau sanctuaire immédiatement. Dans un ancien
monde où le mod vient d'être ajouté, les structures ordinaires apparaissent
dans de nouveaux chunks. Explorer reste nécessaire.

## Déroulement détaillé — contient les conditions de progression

| Chapitre | Ce qui fait avancer |
|---|---|
| Normalité | Vivre une première anomalie ou découvrir un poste d'observation |
| Traces | Vivre trois manifestations d'imitation reconnues par le directeur |
| Imitation | Utiliser le lit deux fois et rencontrer l'Hôte près de son refuge |
| Familiarité | Découvrir sa copie de refuge **ou une Maison décalée** |
| Substitution | Accumuler quatre observations de l'Hôte reconnues par le jeu |
| Reconnaissance | Apporter une caméra et un Sceau de cendre au sanctuaire, puis accomplir le rituel |
| Confrontation | Gagner le duel contre son reflet |

Les observations peuvent avoir été accumulées avant leur chapitre. Un rituel
effectué trop tôt apporte du calme, mais ne remplace pas le rituel final à
accomplir lorsqu'on est prêt. Les quatre pages approfondissent l'enquête et
expliquent les contre-mesures ; les ramasser toutes n'est pas une condition
technique de victoire.

Au chapitre Familiarité, la première tentative de copie est planifiée après
deux minutes de monde, avec un nouvel essai au plus une fois par minute si
le terrain ou la situation ne convient pas. Le délai est sauvegardé. Les
protections du terrain et l'absence d'autre joueur à proximité restent en
place ; la Maison décalée fournit une autre voie pour les groupes.

## Le rituel et la fin

La caméra VHS se fabrique avec sa recette habituelle de verre, fer et cuir
(`penumbra:binoculars`). Le Sceau de cendre se trouve notamment dans les
structures ; l'Hôte peut aussi en laisser tomber. Il est distinct du Sceau
de bannissement utilisé contre d'autres créatures.

Au chapitre Reconnaissance, un clic droit avec le Sceau sur le pilier central
du sanctuaire lance le préambule, puis directement le duel. La caméra doit
être dans l'inventaire avant le lancement ; sinon, le jeu explique ce qui
manque et ne consomme pas le Sceau. Il n'est plus nécessaire d'accomplir un
premier rituel pour simplement débloquer un deuxième rituel identique.

Face au reflet : prendre la caméra, l'observer sans bouger, attendre la
fissure, puis déclencher un **clic gauche bien cadré**. Trois brèches réussies
permettent de gagner. Une fenêtre manquée conserve les fragments obtenus
dans cette tentative. La caméra se recharge entre les photos ; elle n'utilise
pas de pellicule consommable.

La victoire donne le badge, un épilogue et une accalmie de 48 000 ticks de
monde, soit environ 40 minutes à 20 TPS. La victoire reste enregistrée.
Le joueur peut continuer sa survie et terminer sa collection ; les
manifestations peuvent reprendre après l'accalmie. C'est une conclusion
de l'enquête, pas l'arrêt du serveur ni une suppression définitive de l'horreur.

Mort, fuite à plus de 40 blocs, changement de dimension ou déconnexion
interrompent la tentative. Il est possible de revenir avec un nouveau Sceau ;
les fragments du duel interrompu ne sont pas une progression sauvegardée.

## Portée de la vérification

Les tests vérifient les transitions réellement utilisées par le gestionnaire,
la sauvegarde des compteurs et délais, l'indépendance des joueurs, la remise
du carnet, le choix des pistes et la conservation des victoires. La compilation
finale, les ressources et **347 tests dans 68 suites** ont réussi. En jeu,
l'arrivée en survie sans OP, le guide, le refus sans caméra, le lancement du
duel avec une seule offrande et la page obtenue dans un coffre vidé ont été
vérifiés. Une sauvegarde forcée suivie d'un arrêt propre et d'un redémarrage a
conservé le stade 6, la page et le carnet unique ; le guide propose de reprendre
la rencontre sans annoncer une victoire.

Les [résultats et limites](KNOWN_ISSUES.md) et le [protocole détaillé avec les
observations confirmées](TEST_PROTOCOL.md) distinguent ces essais ciblés des
cas encore à jouer. Aucun duel complet de la 0.23.0, inventaire plein en jeu,
campagne naturelle entière ou essai à deux clients simultanés n'est validé
par ce bilan.

Ce parcours suppose les mécanismes narratifs et les événements nécessaires
activés. Désactiver volontairement les événements, l'histoire ou la génération
des structures peut empêcher une campagne naturelle ; les réglages personnels
de confort sur **K** ne désactivent pas ces mécanismes.
