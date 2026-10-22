# Installation de Penumbra 0.24.0

Téléchargez **`penumbra-0.24.0.jar`** sur la
[page de la préversion 0.24.0](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.24.0).
Les archives de sources et le fichier `-sources.jar` servent au développement.
Le pack ZIP accompagne le JAR avec des guides et des résultats de vérification :
il s'installe manuellement et ne s'importe pas comme un modpack.

## Prérequis

| Composant | Version |
|---|---|
| Minecraft | **26.1.2** |
| Fabric Loader | **0.19.3** |
| Fabric API | **0.155.2+26.1.2** |
| Java | **25** |

Ce tableau indique les versions utilisées pour la préparation et les essais.
Fabric API doit être téléchargé et installé séparément.

## Client / partie solo

1. Installez Minecraft 26.1.2 et lancez-le une fois.
2. Installez Fabric Loader pour Minecraft 26.1.2 depuis
   <https://fabricmc.net/use/installer/>.
3. Placez Fabric API et **`penumbra-0.24.0.jar`** dans le dossier `mods/` de
   l'instance.
4. Lancez le profil Fabric. Le journal doit annoncer `penumbra 0.24.0` puis
   `Penumbra initialisé`.

Au premier démarrage, `config/penumbra.json` est créé. Les effets sensibles
peuvent être adoucis avant de jouer : `screamersEnabled: false`,
`cameraShakeEnabled: false`, `distortionEnabled: false`, `fogEnabled: false`,
puis baisse de `jumpscareVolume` et `jumpscareFrequency`.

En jeu, **K** ouvre les réglages personnels de mouvement, flashs, grain,
scintillement et les deux volumes. **Appliquer** les enregistre dans
`config/penumbra-client.json`, sans modifier les règles du serveur.
Les images de visages et écrans noirs de screamer sont retirés en 0.24.0.

## Serveur multijoueur

Installez le même JAR **Penumbra 0.24.0** et Fabric API dans `mods/` sur le serveur **et
sur chaque client**. La configuration serveur pilote la sélection des
événements et les modifications du monde ; les options de rendu, de zoom et
de volume sont aussi lues localement par chaque client. Ne mélangez pas les
versions de Penumbra entre serveur et clients.

**Limite de validation :** la 0.24.0 a passé 390 tests automatisés dans 76 suites,
la compilation et la validation des ressources. Ses essais en jeu utilisent
un seul client connecté à un serveur local et des outils de diagnostic.
Les règles multijoueurs décrites ci-dessous sont implémentées, mais la 0.24.0
n'a pas encore été éprouvée avec deux clients simultanés, une campagne complète
ou une latence distante. Voir les [problèmes connus](KNOWN_ISSUES.md).

Le serveur dédié peut démarrer sans interface graphique. Chaque joueur conserve
un profil de tension et une progression narrative distincts, même si certaines
entités physiques restent visibles par les autres joueurs proches.

Les faux compagnons de la 0.19.0 constituent une exception volontaire : seuls
leurs propriétaires reçoivent l'entité et ses effets. Un témoin voisin ne la voit
pas, ne la percute pas et n'entend pas ses pas, alors que les manifestations
physiques ordinaires et leurs sons spatiaux conservent les règles multijoueur
précédentes.

En 0.24.0, les Hôtes des nouvelles scènes personnelles, leurs sons privés et
leurs particules sont également réservés au joueur concerné. Les créatures
ordinaires restent partagées ; la proximité d'autres joueurs peut empêcher
ou annuler une scène personnelle. Ce comportement demande encore un essai
avec deux clients simultanés.

Les scènes automatiques lourdes sont espacées de 4 secondes entre joueurs
distants d'au plus 32 blocs, avec une priorité tournante. Les groupes plus
éloignés et les dimensions différentes restent indépendants. Les sons d'une
scène sont positionnés et peuvent être partagés avec un compagnon situé à
24 blocs ou moins de sa cible ; les effets visuels et les attaques restent liés
au propriétaire de la scène. Les commandes opérateur forcées ne sont pas
bridées par cette coordination.

## Mise à jour vers 0.24.0

1. Arrêtez complètement le jeu ou le serveur et sauvegardez le monde.
2. Retirez l'ancien JAR Penumbra du dossier `mods/` ; ne conservez qu'une seule
   version.
3. Ajoutez `penumbra-0.24.0.jar` et vérifiez les dépendances du tableau.
4. En multijoueur, mettez à jour le serveur et tous les clients avant de relancer.

Conservez les sauvegardes, photos et fichiers de configuration existants.
Le menu personnel utilise `config/penumbra-client.json` depuis la 0.22.0 ;
les anciens réglages Visages et Rémanences ne peuvent pas réactiver ces images.
Les scénarios de migration anciens ci-dessous décrivent leur comportement
historique ; ils ne constituent pas une nouvelle validation de toutes ces
sauvegardes avec la 0.24.0.

### Historique des migrations de 0.1 à 0.19.0

Les anciens `penumbra.json` sont complétés automatiquement avec les nouvelles
clés. Pour la migration vers le schéma 5, une copie `penumbra.json.pre-v5.bak` est
conservée avant modification. La
clé historique `storyMode` est reprise dans `storyEnabled` si cette dernière
n'existait pas. Les sauvegardes de monde antérieures restent lisibles ; les
structures n'apparaissent que dans des chunks encore jamais générés.
Les photos, cadres et albums de la 0.6.4 restent lisibles. En l'absence du
nouveau marqueur `projectionist`, un ancien tirage est traité comme une photo
ordinaire et ne peut pas déclencher le Projectionniste.
La 0.8.0 ne demandait aucune conversion manuelle supplémentaire. Elle conserve
le **Sceau de cendre** narratif historique `penumbra:ash_sigil` et ajoute
séparément le **Sceau de bannissement** consommable `penumbra:ash_ward` ; aucun
objet existant n'est renommé ou remplacé.
La 0.8.1 ne demande pas davantage de conversion : elle augmente uniquement la
capacité maximale de l'album à 2 048 photos par monde, sans modifier le quota
de 128 photos par joueur ni le format des tirages existants.
La 0.9.0 ajoute aux photos existantes un titre vide et une visibilité privée
par défaut. Aucun tirage ancien n'est publié, renommé ou supprimé pendant la
migration.
La 0.10.0 ajoute des listes de preuves et le marqueur **Important** avec des
valeurs vides ou fausses par défaut, ainsi qu'un dossier personnel persistant.
Les UUID et pixels existants restent inchangés. Un ancien tirage marqué
`projectionist` conserve cette preuve ; aucune autre catégorie n'est inventée
rétroactivement. Le dossier se remplit lorsque le propriétaire ouvre l'album
après le développement des tirages concernés.
La 0.11.0 ajoute quatre réglages de L'Absent et fait passer la configuration au
schéma 3. Les fichiers antérieurs sont sauvegardés puis complétés sans perdre
les réglages existants. Les anciennes photos restent identiques ; seules les
nouvelles captures où L'Absent est réellement cadré peuvent recevoir sa double
exposition.
La 0.12.0 ajoute les arcs adaptatifs, la contamination des refuges, les scènes
liées et leurs limites, puis fait passer le schéma au niveau 4 sans modifier les
profils, photos ou structures existants.
La 0.19.0 ajoute quatre réglages de perception et fait passer le schéma au niveau
5. Les nouvelles scènes sont transitoires : aucune perception active n'est
sauvegardée dans le monde et les données existantes ne demandent aucune conversion.

## Désinstallation

Sauvegardez le monde, arrêtez le jeu puis retirez `penumbra-0.24.0.jar`.
Les données persistantes deviennent inertes, les objets Penumbra peuvent
disparaître des inventaires et les restaurations différées ne s'exécuteront
plus. Les structures composées de blocs vanilla restent dans le monde.
Vous pouvez ensuite supprimer `config/penumbra.json` et
`config/penumbra-client.json` si vous ne souhaitez pas conserver les réglages.

## Diagnostic rapide

- Un crash avant le menu : vérifiez les quatre versions du tableau et retirez
  les doublons de JAR.
- Un son ou une texture manquante : consultez `logs/latest.log` et vérifiez que
  le JAR n'a pas été extrait dans `mods/`.
- Une commande de test qui échoue : `/penumbra projectionist test` exige un sol
  chargé derrière le joueur, `/penumbra rampant test` une vraie grotte sombre,
  `/penumbra absent test` un emplacement au sol sans collision, et les deux
  commandes `/penumbra scuttler` un sol sec valide hors mode Paisible.
- Un faux compagnon absent : vérifiez `individualPerceptionsEnabled` et
  `falseCompanionsEnabled`, quittez le mode Spectateur/Paisible puis utilisez
  `/penumbra perception companion`. `/penumbra perception status` permet de
  vérifier la scène et `/penumbra perception clear` de la dissiper.
- Un Sceau de bannissement qui ne se consume pas : `penumbra:ash_ward` n'agit
  que sur
  les monstres Penumbra à 16 blocs ou moins ; une utilisation sans cible valide
  laisse volontairement la pile intacte.
- Une configuration invalide est sauvegardée sous
  `penumbra.json.broken[.<horodatage>]`, puis remplacée par des valeurs saines.
