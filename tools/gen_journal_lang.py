#!/usr/bin/env python3
"""Injecte le texte du Relevé (0.18.0) dans les trois fichiers de langue.

Le carnet est tenu par **Elias Warrick**, arpenteur,
mort quelque part entre la troisième et la quatrième année de son séjour. Il
écrit court, il numérote, il se corrige. Il ne s'attendrit qu'une fois, à la
dernière page, et encore : sur son travail, pas sur lui.

Règles d'écriture tenues sur toutes les entrées :
  - jamais de chiffre de jeu (pas de « 22 blocs »), toujours une mesure humaine
    (« la portée d'une voix », « trois pas ») ;
  - chaque entrée de créature finit par une contre-mesure vérifiable ;
  - la ligne `faded` est la seule survivante d'une page délavée : elle doit
    intriguer sans rien apprendre d'utilisable ;
  - les quatre pages arrachées portent les contre-mesures les plus fortes du
    mod. C'est ce qui justifie d'aller les chercher.

Usage : python3 tools/gen_journal_lang.py
"""

import collections
import json
import pathlib

LANG = pathlib.Path(__file__).resolve().parent.parent / (
    "src/main/resources/assets/penumbra/lang")

# ————————————————————————————— FRANÇAIS —————————————————————————————

FR = {
    # ——— Objets ———
    "item.penumbra.survey_journal": "Le Relevé",
    "item.penumbra.survey_journal.lore1": "Le cuir a gonflé. Il ne ferme plus.",
    "item.penumbra.survey_journal.lore2": "Quelqu'un a tenu ça pendant des années.",
    "item.penumbra.torn_page": "Page arrachée",
    "item.penumbra.torn_page.desc": "Arrachée net, près de la reliure.",

    # ——— Écran ———
    "screen.penumbra.journal.title": "Le Relevé",
    "screen.penumbra.journal.footer": "%s / %s   ·   %s pages sur %s",
    "screen.penumbra.journal.faded": "L'humidité a mangé le reste.",
    "screen.penumbra.journal.torn_title": "Page manquante",
    "screen.penumbra.journal.torn_body":
        "Il ne reste que le talon. La page a été arrachée d'un coup sec, "
        "et pas par accident : les autres tiennent encore.",
    "screen.penumbra.journal.hint_label": "Noté sur la page d'en face :",

    # ——— Messages ———
    "journal.penumbra.filled": "Le Relevé se remplit : %s",
    "journal.penumbra.page_inserted": "Vous glissez la page dans le Relevé : %s",
    "journal.penumbra.page_already": "Cette page est déjà dans le carnet.",
    "journal.penumbra.page_illegible": "L'encre a coulé. Cette page ne dit plus rien.",
    "journal.penumbra.disabled": "Le carnet reste fermé.",

    # ——— Panneau du Bûcher ———
    "sign.penumbra.pyre.line1": "je garde",
    "sign.penumbra.pyre.line2": "le feu allumé",
    "sign.penumbra.pyre.line3": "pour qu'on me",
    "sign.penumbra.pyre.line4": "trouve",

    # ——— Frontispice ———
    "journal.penumbra.frontispiece.title": "Le Relevé",
    "journal.penumbra.frontispiece.subtitle": "E. Warrick — commencé le 12 du troisième mois",
    "journal.penumbra.frontispiece.faded": "",
    "journal.penumbra.frontispiece.line1":
        "Je m'appelle Elias Warrick. J'étais arpenteur. Je ne le suis plus "
        "depuis longtemps : on n'arpente pas un endroit qui bouge.",
    "journal.penumbra.frontispiece.line2":
        "Si vous lisez ceci, c'est que le feu s'est éteint, ou que je n'ai pas "
        "pu le rallumer. Les deux veulent dire la même chose.",
    "journal.penumbra.frontispiece.line3":
        "Ce carnet n'est pas un avertissement. J'en ai écrit un, la première "
        "année. Il ne servait à rien. Personne ne part.",
    "journal.penumbra.frontispiece.line4":
        "C'est un relevé. Une page par chose. Ce qu'elle est, ce qu'elle fait, "
        "et ce qui marche contre elle. Rien d'autre.",
    "journal.penumbra.frontispiece.line5":
        "J'ai arraché quatre pages et je les ai laissées ailleurs. Ce sont les "
        "quatre qui comptent. Je ne voulais pas qu'on les trouve par hasard.",
    "journal.penumbra.frontispiece.line6":
        "Continuez le relevé. C'est la seule chose qui m'ait tenu debout.",

    # ——— L'Hôte ———
    "journal.penumbra.host.title": "l'Hôte",
    "journal.penumbra.host.subtitle": "première observation : deuxième mois",
    "journal.penumbra.host.faded":
        "…toujours au bord. Jamais au milieu. Je crois qu'il attend qu'on…",
    "journal.penumbra.host.line1":
        "Le premier que j'aie vu, et celui que je comprends le moins. Une "
        "silhouette pâle, plus haute qu'un homme, qui se tient au bord de ce "
        "qu'on éclaire.",
    "journal.penumbra.host.line2":
        "Il n'apparaît jamais devant vous. Il est déjà là quand vous vous "
        "retournez, et il n'y est plus quand vous insistez.",
    "journal.penumbra.host.line3":
        "Il apprend. J'en suis certain maintenant. Il marche comme moi. Il "
        "s'arrête où je m'arrête. La deuxième année, il boitait — je boitais.",
    "journal.penumbra.host.line4":
        "Le fixer trop longtemps est une erreur. Il se fige d'abord. C'est le "
        "seul avertissement que vous aurez.",
    "journal.penumbra.host.line5":
        "Contre lui : la lumière ne suffit pas, mais elle le tient à distance. "
        "Et ne le regardez pas dans les yeux plus de trois secondes.",

    # ——— L'Observateur ———
    "journal.penumbra.watcher.title": "l'Observateur",
    "journal.penumbra.watcher.subtitle": "première observation : quatrième mois",
    "journal.penumbra.watcher.faded":
        "…debout, à distance, sans jamais s'approcher. J'ai compté onze minutes avant que…",
    "journal.penumbra.watcher.line1":
        "Une forme noire, immobile, plantée à mi-distance. Elle ne s'approche "
        "pas. Elle ne fait rien du tout. C'est précisément le problème.",
    "journal.penumbra.watcher.line2":
        "Elle regarde. Tant qu'on ne la regarde pas en retour, elle reste. "
        "Le premier qui cède, c'est toujours moi.",
    "journal.penumbra.watcher.line3":
        "Il m'est arrivé d'en voir plusieurs le même soir, tournées vers le "
        "même point. Je n'étais pas à ce point-là. Je n'ai pas été voir.",
    "journal.penumbra.watcher.line4":
        "Contre lui : soutenir son regard le fait partir. C'est la seule chose "
        "de ce pays qui recule quand on avance.",

    # ——— La Cavaleuse ———
    "journal.penumbra.scuttler.title": "la Cavaleuse",
    "journal.penumbra.scuttler.subtitle": "première observation : cinquième mois",
    "journal.penumbra.scuttler.faded":
        "…une chose basse et rapide en travers du chemin. Le cœur avant les yeux…",
    "journal.penumbra.scuttler.line1":
        "Petite. Basse. Traverse le champ de vision en une seconde et demie et "
        "disparaît. La première fois, j'ai lâché ma lanterne.",
    "journal.penumbra.scuttler.line2":
        "Je l'ai crue dangereuse pendant un mois entier. Elle ne l'est pas. "
        "Elle ne mord pas, elle ne suit pas, elle ne fait rien.",
    "journal.penumbra.scuttler.line3":
        "Sa fonction est ailleurs : elle vous fait tourner la tête. Regardez "
        "ce qui se passe de l'autre côté pendant que vous la regardez, elle.",
    "journal.penumbra.scuttler.line4":
        "Contre elle : rien à faire. Mais ne suivez jamais des yeux ce qui "
        "court. Regardez toujours le côté opposé.",

    # ——— Le Contrefait ———
    "journal.penumbra.contrefait.title": "le Contrefait",
    "journal.penumbra.contrefait.subtitle": "première observation : sixième mois",
    "journal.penumbra.contrefait.faded":
        "…mal monté. Comme un premier essai que personne n'a jeté…",
    "journal.penumbra.contrefait.line1":
        "Une copie ratée. Les proportions ne tiennent pas, la peau est trop "
        "verte, la démarche est celle de quelqu'un qui apprend à marcher.",
    "journal.penumbra.contrefait.line2":
        "Je pense que c'est un brouillon. Quelque chose s'est entraîné sur "
        "lui avant de faire mieux. Beaucoup mieux.",
    "journal.penumbra.contrefait.line3":
        "Il est bruyant, maladroit, et il vous voit venir de loin. C'est le "
        "seul de la liste dont on peut dire qu'il est franc.",
    "journal.penumbra.contrefait.line4":
        "Contre lui : il abandonne vite. Tenez trente pas et il ne suivra pas. "
        "Ne prenez pas cette facilité pour une règle générale.",

    # ——— Le Rampant ———
    "journal.penumbra.rampant.title": "le Rampant",
    "journal.penumbra.rampant.subtitle": "première observation : huitième mois, sous terre",
    "journal.penumbra.rampant.faded":
        "…au plafond. J'avais éclairé le sol pendant huit mois. Le sol…",
    "journal.penumbra.rampant.line1":
        "Sous terre uniquement. Il ne marche pas au sol : il se tient aux "
        "parois et aux plafonds, à plat, et il descend quand la galerie est "
        "assez étroite.",
    "journal.penumbra.rampant.line2":
        "Huit mois à éclairer devant moi. Huit mois à ne jamais lever la tête. "
        "Notez ça quelque part, si vous ne notez rien d'autre.",
    "journal.penumbra.rampant.line3":
        "Il attend l'obscurité totale pour lâcher prise. Une seule torche "
        "posée au bon endroit annule toute une galerie.",
    "journal.penumbra.rampant.line4":
        "Il ne suit pas dehors. Je ne l'ai jamais vu franchir une entrée de "
        "grotte. Je ne sais pas s'il ne peut pas ou s'il ne veut pas.",
    "journal.penumbra.rampant.line5":
        "Contre lui : de la lumière au plafond, pas au sol. Et regardez en "
        "l'air toutes les trente secondes. Faites-en une habitude.",

    # ——— Le Projectionniste ———
    "journal.penumbra.projectionist.title": "le Projectionniste",
    "journal.penumbra.projectionist.subtitle": "première observation : dixième mois",
    "journal.penumbra.projectionist.faded":
        "…il ne se montre pas. Il se développe. Ce n'est pas la même chose…",
    "journal.penumbra.projectionist.line1":
        "Celui-ci n'apparaît pas dans le monde. Il apparaît sur les tirages.",
    "journal.penumbra.projectionist.line2":
        "J'ai photographié un couloir vide. Sur l'image, il y avait quelqu'un "
        "au fond. J'y suis retourné avec la lampe. Rien.",
    "journal.penumbra.projectionist.line3":
        "Une fois sur sept environ. J'ai tenu le compte pendant deux cents "
        "clichés, ce qui vous dira l'état dans lequel j'étais.",
    "journal.penumbra.projectionist.line4":
        "Il n'a jamais rien fait d'autre. Aucune attaque, aucun bruit. Il "
        "veut être vu, et il a compris comment on regarde.",
    "journal.penumbra.projectionist.line5":
        "Contre lui : rien à craindre. Mais ne jetez pas les tirages ratés. "
        "Ce sont les seuls qui disent la vérité.",

    # ——— Le Faux-Semblant ———
    "journal.penumbra.faux_creature.title": "le Faux-Semblant",
    "journal.penumbra.faux_creature.subtitle": "première observation : onzième mois",
    "journal.penumbra.faux_creature.faded":
        "…une vache. Une vache parfaitement ordinaire qui n'a pas cligné une seule fois…",
    "journal.penumbra.faux_creature.line1":
        "Une bête du pays. Une vraie, en apparence : vache, mouton, poule. "
        "Elle broute, elle se tient là, elle ne fait rien de remarquable.",
    "journal.penumbra.faux_creature.line2":
        "Le défaut est minuscule. Elle ne fait aucun bruit. Ou elle vous fixe "
        "sans jamais détourner la tête. Ou elle est plantée face à un mur.",
    "journal.penumbra.faux_creature.line3":
        "Et parfois il n'y a pas de défaut. J'ai mis un an à l'admettre. "
        "Certaines copies sont parfaites, et je ne peux rien en écrire.",
    "journal.penumbra.faux_creature.line4":
        "Elle se dissout dès qu'on la touche. En cendres, avec un bruit de "
        "gorge. Aucune vraie bête n'a jamais été blessée par ma faute.",
    "journal.penumbra.faux_creature.line5":
        "Contre elle : approchez et tendez la main. C'est désagréable, c'est "
        "sans danger, et c'est la seule preuve qui vaille.",

    # ——— L'Absent ———
    "journal.penumbra.absent.title": "L'Absent",
    "journal.penumbra.absent.subtitle": "première observation : deuxième année",
    "journal.penumbra.absent.faded":
        "…il portait mon manteau. Je porte encore mon manteau…",
    "journal.penumbra.absent.line1":
        "Celui-ci est le pire, et je vais essayer de l'écrire calmement.",
    "journal.penumbra.absent.line2":
        "Il prend l'apparence de quelqu'un que vous connaissez. Exactement. "
        "Le visage, les vêtements, la façon de poser les pieds.",
    "journal.penumbra.absent.line3":
        "Il choisit toujours une personne qui vient de sortir de votre champ "
        "de vision. Si vous êtes seul, il vous choisit, vous.",
    "journal.penumbra.absent.line4":
        "Il se trompe, mais tard. D'abord un geste répété deux fois. Puis un "
        "pas qui ne fait pas de bruit. Puis il arrête de faire semblant.",
    "journal.penumbra.absent.line5":
        "J'ai vécu quatre jours avec l'un d'eux. Je ne l'ai pas su. Je n'ai "
        "compris qu'en trouvant l'autre — le vrai — dans la neige.",
    "journal.penumbra.absent.line6":
        "Contre lui : le flash. Voir page arrachée. C'est pour ça que je l'ai "
        "arrachée.",

    # ——— L'Écouteur ———
    "journal.penumbra.listener.title": "L'Écouteur",
    "journal.penumbra.listener.subtitle": "première observation : deuxième année, hiver",
    "journal.penumbra.listener.faded":
        "…il n'a pas d'yeux. Il n'en a jamais eu. J'ai cru que c'était une chance…",
    "journal.penumbra.listener.line1":
        "Le plus grand de tous. Maigre à faire mal. Et là où devraient être "
        "les yeux, la peau a été tirée et recousue.",
    "journal.penumbra.listener.line2":
        "Il ne voit rien. Rien du tout. J'ai agité une lanterne devant lui "
        "à deux pas : aucune réaction.",
    "journal.penumbra.listener.line3":
        "Il entend. C'est tout ce qu'il fait. Sa tête est toujours tournée "
        "vers le dernier bruit — pas vers vous. Regardez sa tête, pas ses pieds.",
    "journal.penumbra.listener.line4":
        "Marcher normalement s'entend à la portée d'une voix. Courir s'entend "
        "trois fois plus loin. Frapper de la pierre s'entend d'un versant à "
        "l'autre, même immobile.",
    "journal.penumbra.listener.line5":
        "Il se trompe d'un pas ou deux. Il vient vers le bruit, pas vers vous. "
        "C'est cette erreur-là qui m'a sauvé quatre fois.",
    "journal.penumbra.listener.line6":
        "Contre lui : voir page arrachée. Et si vous n'avez pas trouvé cette "
        "page-là — arrêtez-vous. Complètement. Tout de suite.",

    # ——— L'Originel ———
    "journal.penumbra.originel.title": "l'Originel",
    "journal.penumbra.originel.subtitle": "observations : trois en quatre ans",
    "journal.penumbra.originel.faded":
        "…trois fois en quatre ans. Je n'écris pas ce qu'il a fait la troisième…",
    "journal.penumbra.originel.line1":
        "Trois fois. En quatre ans. C'est tout ce que j'ai, et c'est déjà "
        "trop pour ce que ça m'a coûté d'écrire.",
    "journal.penumbra.originel.line2":
        "Les autres imitent. Lui, non. J'ai la conviction — sans preuve — "
        "que les autres imitent quelque chose, et que ce quelque chose est lui.",
    "journal.penumbra.originel.line3":
        "Il n'était pas au bord de la lumière. Il était au milieu. En plein "
        "jour, la deuxième fois.",
    "journal.penumbra.originel.line4":
        "Il n'a rien fait. Aucune des trois fois. Je crois qu'il est venu "
        "voir où j'en étais de ce carnet.",
    "journal.penumbra.originel.line5":
        "Contre lui : je n'ai rien. Quatre ans, et je n'ai rien à écrire ici. "
        "C'est la seule ligne de ce relevé dont j'ai honte.",

    # ——— Pages arrachées ———
    "journal.penumbra.method_camera.title": "Le flash",
    "journal.penumbra.method_camera.subtitle": "page arrachée — première des quatre",
    "journal.penumbra.method_camera.faded":
        "Le flash. Bien cadré, sans vitre, sans pluie. Il ne supporte pas d'être vu net.",
    "journal.penumbra.method_camera.hint":
        "Je l'ai laissée là où je regardais le plus loin. Là-haut, sur les "
        "planches, avec la lunette fêlée.",
    "journal.penumbra.method_camera.line1":
        "La chambre photographique est la meilleure arme de ce pays, et elle "
        "ne tue rien. C'est pour ça qu'elle marche.",
    "journal.penumbra.method_camera.line2":
        "Un flash bien cadré, à courte portée, sans vitre ni obstacle entre "
        "vous et lui : L'Absent perd sa figure empruntée pendant plusieurs "
        "secondes. Il n'y peut rien. C'est mécanique.",
    "journal.penumbra.method_camera.line3":
        "Le tirage garde la trace. Développez-le et vous verrez deux "
        "silhouettes superposées : celle qu'il montrait, et l'autre.",
    "journal.penumbra.method_camera.line4":
        "Le même flash dissout un Faux-Semblant sur place, sans avoir à le "
        "toucher. Préférez ça : toucher, c'est se pencher, et se pencher "
        "prend du temps.",
    "journal.penumbra.method_camera.line5":
        "Attention : à travers une vitre, rien. De dos, rien. Trop loin, rien. "
        "Le cadrage est tout, et vous n'avez qu'un essai.",
    "journal.penumbra.method_camera.line6":
        "Gardez toujours une charge. Toujours. J'ai perdu un ami parce que "
        "j'avais photographié un oiseau une heure plus tôt.",

    "journal.penumbra.method_refuge.title": "Les refuges",
    "journal.penumbra.method_refuge.subtitle": "page arrachée — deuxième des quatre",
    "journal.penumbra.method_refuge.faded":
        "Il copie l'endroit où vous dormez. Comptez les fenêtres avant d'entrer.",
    "journal.penumbra.method_refuge.hint":
        "Je l'ai laissée dans la maison qui n'est pas droite. Vous saurez "
        "laquelle : on y compte mal les pas.",
    "journal.penumbra.method_refuge.line1":
        "Ce que vous construisez sera copié. Pas volé, pas détruit : copié. "
        "Une deuxième fois, quelque part à proximité.",
    "journal.penumbra.method_refuge.line2":
        "La copie est bonne mais jamais exacte. Une fenêtre en trop, un "
        "escalier d'une marche trop court, une porte du mauvais côté.",
    "journal.penumbra.method_refuge.line3":
        "Comptez vos fenêtres. Écrivez le nombre sur la porte. C'est ridicule "
        "et c'est ce qui m'a évité de dormir trois fois dans la mauvaise.",
    "journal.penumbra.method_refuge.line4":
        "Plus vous revenez au même refuge, plus il se dégrade : un coffre qui "
        "sonne faux, une torche impossible, une photo que vous n'avez pas prise.",
    "journal.penumbra.method_refuge.line5":
        "Rien de tout cela n'est réel et rien n'est permanent. Sortez, "
        "attendez, revenez : votre maison sera redevenue la vôtre.",

    "journal.penumbra.method_stillness.title": "L'immobilité",
    "journal.penumbra.method_stillness.subtitle": "page arrachée — troisième des quatre",
    "journal.penumbra.method_stillness.faded":
        "Ne plus bouger du tout. Pas presque. Pas doucement. Du tout.",
    "journal.penumbra.method_stillness.hint":
        "Je l'ai laissée au cercle de cendre. Il fallait bien que cet endroit "
        "serve à quelque chose.",
    "journal.penumbra.method_stillness.line1":
        "Contre L'Écouteur, il n'y a qu'une chose, et elle est difficile pour "
        "une raison que je n'avais pas prévue : elle ne demande aucun courage, "
        "elle demande de ne pas en avoir.",
    "journal.penumbra.method_stillness.line2":
        "Arrêtez-vous. Complètement. Pas presque, pas doucement, pas "
        "accroupi-en-reculant. Immobile. Vous devenez inaudible, et "
        "inaudible veut dire invisible pour lui.",
    "journal.penumbra.method_stillness.line3":
        "Il passera à un pas de vous. Il s'arrêtera. Il balaiera la tête de "
        "gauche à droite pendant un temps qui vous paraîtra très long. Puis "
        "il ira ailleurs.",
    "journal.penumbra.method_stillness.line4":
        "Se déplacer accroupi ne s'entend qu'à trois pas. C'est la seule "
        "façon de bouger tant qu'il est près.",
    "journal.penumbra.method_stillness.line5":
        "Et si vous devez absolument fuir : cassez une pierre au loin d'abord. "
        "Il ira la fouiller. Ce n'est pas de la ruse, c'est tout ce qu'il sait "
        "faire.",

    "journal.penumbra.last_page.title": "La dernière page",
    "journal.penumbra.last_page.subtitle": "page arrachée — quatrième des quatre",
    "journal.penumbra.last_page.faded":
        "Ce n'est pas venu me chercher. Je suis allé le voir.",
    "journal.penumbra.last_page.hint":
        "Celle-là, je l'ai jetée dans le puits. Je n'étais pas certain de "
        "vouloir qu'elle soit lue.",
    "journal.penumbra.last_page.line1":
        "Quatrième année. Je n'ai plus rien à relever. J'ai fait le tour.",
    "journal.penumbra.last_page.line2":
        "Je garde le feu allumé depuis trois ans pour qu'on me trouve. "
        "Personne n'est venu. Je crois que la fumée ne sort pas d'ici.",
    "journal.penumbra.last_page.line3":
        "Alors j'ai arrêté d'attendre. Demain je descends au puits, et je "
        "vais voir jusqu'où il va. C'est la seule ligne du relevé que je "
        "n'ai pas pu remplir.",
    "journal.penumbra.last_page.line4":
        "Si vous lisez ceci, c'est que vous êtes descendu aussi. Alors vous "
        "savez déjà ce que j'ai trouvé, et ce carnet ne vous apprend rien de "
        "plus que ce que vous avez sous les yeux.",
    "journal.penumbra.last_page.line5":
        "Une dernière chose, et c'est la seule que je n'aie pas vérifiée : "
        "je ne crois pas que ces choses nous veuillent du mal. Je crois "
        "qu'elles essaient d'apprendre à être nous, et qu'elles s'y prennent mal.",
    "journal.penumbra.last_page.line6":
        "Continuez le relevé. Écrivez mieux que moi.   — E. W.",
}

# ————————————————————————————— ENGLISH —————————————————————————————

EN = {
    "item.penumbra.survey_journal": "The Survey",
    "item.penumbra.survey_journal.lore1": "The leather has swollen. It won't close.",
    "item.penumbra.survey_journal.lore2": "Someone kept this for years.",
    "item.penumbra.torn_page": "Torn Page",
    "item.penumbra.torn_page.desc": "Torn clean, close to the binding.",

    "screen.penumbra.journal.title": "The Survey",
    "screen.penumbra.journal.footer": "%s / %s   ·   %s pages of %s",
    "screen.penumbra.journal.faded": "The damp ate the rest.",
    "screen.penumbra.journal.torn_title": "Missing page",
    "screen.penumbra.journal.torn_body":
        "Only the stub is left. This page was torn out in one pull, and not by "
        "accident: the others are still holding.",
    "screen.penumbra.journal.hint_label": "Noted on the facing page:",

    "journal.penumbra.filled": "The Survey fills in: %s",
    "journal.penumbra.page_inserted": "You slide the page back into the Survey: %s",
    "journal.penumbra.page_already": "That page is already in the book.",
    "journal.penumbra.page_illegible": "The ink has run. This page says nothing now.",
    "journal.penumbra.disabled": "The book stays shut.",

    "sign.penumbra.pyre.line1": "keeping",
    "sign.penumbra.pyre.line2": "the fire lit",
    "sign.penumbra.pyre.line3": "so someone",
    "sign.penumbra.pyre.line4": "finds me",

    "journal.penumbra.frontispiece.title": "The Survey",
    "journal.penumbra.frontispiece.subtitle": "E. Warrick — begun the 12th of the third month",
    "journal.penumbra.frontispiece.faded": "",
    "journal.penumbra.frontispiece.line1":
        "My name is Elias Warrick. I was a surveyor. I haven't been one for a "
        "long time: you cannot survey a place that moves.",
    "journal.penumbra.frontispiece.line2":
        "If you are reading this, the fire went out, or I could not relight it. "
        "Both mean the same thing.",
    "journal.penumbra.frontispiece.line3":
        "This book is not a warning. I wrote one of those, the first year. It "
        "was no use. Nobody leaves.",
    "journal.penumbra.frontispiece.line4":
        "It is a survey. One page per thing. What it is, what it does, and what "
        "works against it. Nothing else.",
    "journal.penumbra.frontispiece.line5":
        "I tore out four pages and left them elsewhere. They are the four that "
        "matter. I did not want them found by accident.",
    "journal.penumbra.frontispiece.line6":
        "Continue the survey. It is the only thing that kept me upright.",

    "journal.penumbra.host.title": "the Host",
    "journal.penumbra.host.subtitle": "first sighting: second month",
    "journal.penumbra.host.faded":
        "…always at the edge. Never in the middle. I think it waits for us to…",
    "journal.penumbra.host.line1":
        "The first one I saw, and the one I understand least. A pale shape, "
        "taller than a man, standing at the edge of whatever we light.",
    "journal.penumbra.host.line2":
        "It never appears in front of you. It is already there when you turn "
        "around, and gone when you insist.",
    "journal.penumbra.host.line3":
        "It learns. I am certain of it now. It walks the way I walk. It stops "
        "where I stop. The second year it limped — I was limping.",
    "journal.penumbra.host.line4":
        "Staring too long is a mistake. It freezes first. That is the only "
        "warning you will get.",
    "journal.penumbra.host.line5":
        "Against it: light is not enough, but it keeps it back. And do not "
        "hold its gaze for more than three seconds.",

    "journal.penumbra.watcher.title": "the Watcher",
    "journal.penumbra.watcher.subtitle": "first sighting: fourth month",
    "journal.penumbra.watcher.faded":
        "…standing off, never coming closer. I counted eleven minutes before…",
    "journal.penumbra.watcher.line1":
        "A black shape, motionless, planted at middle distance. It does not "
        "approach. It does nothing at all. That is precisely the problem.",
    "journal.penumbra.watcher.line2":
        "It watches. As long as you do not watch back, it stays. The one who "
        "gives in is always me.",
    "journal.penumbra.watcher.line3":
        "I once saw several in one evening, all turned toward the same point. "
        "I was not at that point. I did not go and look.",
    "journal.penumbra.watcher.line4":
        "Against it: hold its gaze and it leaves. It is the only thing in this "
        "country that retreats when you advance.",

    "journal.penumbra.scuttler.title": "the Scuttler",
    "journal.penumbra.scuttler.subtitle": "first sighting: fifth month",
    "journal.penumbra.scuttler.faded":
        "…something low and quick across the path. The heart before the eyes…",
    "journal.penumbra.scuttler.line1":
        "Small. Low. Crosses your field of view in a second and a half and is "
        "gone. The first time, I dropped my lantern.",
    "journal.penumbra.scuttler.line2":
        "I thought it dangerous for a whole month. It is not. It does not "
        "bite, it does not follow, it does nothing.",
    "journal.penumbra.scuttler.line3":
        "Its purpose is elsewhere: it turns your head. Watch what happens on "
        "the other side while you are watching it.",
    "journal.penumbra.scuttler.line4":
        "Against it: nothing to do. But never follow what runs with your eyes. "
        "Always look the opposite way.",

    "journal.penumbra.contrefait.title": "the Counterfeit",
    "journal.penumbra.contrefait.subtitle": "first sighting: sixth month",
    "journal.penumbra.contrefait.faded":
        "…badly assembled. Like a first attempt nobody threw away…",
    "journal.penumbra.contrefait.line1":
        "A failed copy. The proportions do not hold, the skin is too green, "
        "the walk is that of someone learning to walk.",
    "journal.penumbra.contrefait.line2":
        "I believe it is a draft. Something practised on it before doing "
        "better. Much better.",
    "journal.penumbra.contrefait.line3":
        "It is loud, clumsy, and it sees you coming from far off. It is the "
        "only one on this list you could call honest.",
    "journal.penumbra.contrefait.line4":
        "Against it: it gives up quickly. Hold thirty paces and it will not "
        "follow. Do not mistake that mercy for a general rule.",

    "journal.penumbra.rampant.title": "the Crawler",
    "journal.penumbra.rampant.subtitle": "first sighting: eighth month, underground",
    "journal.penumbra.rampant.faded":
        "…on the ceiling. I had been lighting the floor for eight months. The floor…",
    "journal.penumbra.rampant.line1":
        "Underground only. It does not walk on the ground: it holds to walls "
        "and ceilings, flat, and comes down when the passage is narrow enough.",
    "journal.penumbra.rampant.line2":
        "Eight months lighting the way ahead of me. Eight months never looking "
        "up. Write that down somewhere, if you write down nothing else.",
    "journal.penumbra.rampant.line3":
        "It waits for full darkness to let go. One torch placed in the right "
        "spot cancels an entire gallery.",
    "journal.penumbra.rampant.line4":
        "It does not follow outside. I have never seen one cross a cave mouth. "
        "I do not know whether it cannot or will not.",
    "journal.penumbra.rampant.line5":
        "Against it: light on the ceiling, not the floor. And look up every "
        "thirty seconds. Make a habit of it.",

    "journal.penumbra.projectionist.title": "the Projectionist",
    "journal.penumbra.projectionist.subtitle": "first sighting: tenth month",
    "journal.penumbra.projectionist.faded":
        "…it does not show itself. It develops. That is not the same thing…",
    "journal.penumbra.projectionist.line1":
        "This one does not appear in the world. It appears on the prints.",
    "journal.penumbra.projectionist.line2":
        "I photographed an empty corridor. In the image there was someone at "
        "the far end. I went back with the lamp. Nothing.",
    "journal.penumbra.projectionist.line3":
        "About one shot in seven. I kept count over two hundred plates, which "
        "should tell you what state I was in.",
    "journal.penumbra.projectionist.line4":
        "It has never done anything else. No attack, no sound. It wants to be "
        "seen, and it has worked out how we look.",
    "journal.penumbra.projectionist.line5":
        "Against it: nothing to fear. But do not throw away the failed prints. "
        "They are the only ones telling the truth.",

    "journal.penumbra.faux_creature.title": "the Semblance",
    "journal.penumbra.faux_creature.subtitle": "first sighting: eleventh month",
    "journal.penumbra.faux_creature.faded":
        "…a cow. A perfectly ordinary cow that did not blink once…",
    "journal.penumbra.faux_creature.line1":
        "A beast of the country. A real one, to look at: cow, sheep, hen. It "
        "grazes, it stands there, it does nothing remarkable.",
    "journal.penumbra.faux_creature.line2":
        "The flaw is tiny. It makes no sound at all. Or it stares at you and "
        "never turns its head. Or it stands facing a wall.",
    "journal.penumbra.faux_creature.line3":
        "And sometimes there is no flaw. It took me a year to admit that. "
        "Some copies are perfect, and I can write nothing about them.",
    "journal.penumbra.faux_creature.line4":
        "It dissolves the moment you touch it. Into ash, with a sound like a "
        "throat. No real animal has ever been hurt on my account.",
    "journal.penumbra.faux_creature.line5":
        "Against it: walk up and put out your hand. It is unpleasant, it is "
        "safe, and it is the only proof worth anything.",

    "journal.penumbra.absent.title": "the Absent",
    "journal.penumbra.absent.subtitle": "first sighting: second year",
    "journal.penumbra.absent.faded":
        "…it was wearing my coat. I am still wearing my coat…",
    "journal.penumbra.absent.line1":
        "This one is the worst, and I am going to try to write it calmly.",
    "journal.penumbra.absent.line2":
        "It takes the shape of someone you know. Exactly. The face, the "
        "clothes, the way they set their feet down.",
    "journal.penumbra.absent.line3":
        "It always picks someone who has just left your field of view. If you "
        "are alone, it picks you.",
    "journal.penumbra.absent.line4":
        "It makes mistakes, but late. First a gesture repeated twice. Then a "
        "step that makes no sound. Then it stops pretending.",
    "journal.penumbra.absent.line5":
        "I lived four days with one of them. I did not know. I only understood "
        "when I found the other one — the real one — in the snow.",
    "journal.penumbra.absent.line6":
        "Against it: the flash. See torn page. That is why I tore it out.",

    "journal.penumbra.listener.title": "the Listener",
    "journal.penumbra.listener.subtitle": "first sighting: second year, winter",
    "journal.penumbra.listener.faded":
        "…it has no eyes. It never had any. I thought that was luck…",
    "journal.penumbra.listener.line1":
        "The tallest of them all. Painfully thin. And where the eyes should "
        "be, the skin has been pulled over and sewn shut.",
    "journal.penumbra.listener.line2":
        "It sees nothing. Nothing at all. I waved a lantern in front of it at "
        "two paces: no reaction.",
    "journal.penumbra.listener.line3":
        "It hears. That is all it does. Its head is always turned toward the "
        "last sound — not toward you. Watch its head, not its feet.",
    "journal.penumbra.listener.line4":
        "Walking normally carries as far as a voice. Running carries three "
        "times further. Striking stone carries from one slope to the other, "
        "even standing still.",
    "journal.penumbra.listener.line5":
        "It is off by a pace or two. It comes toward the sound, not toward "
        "you. That error saved me four times.",
    "journal.penumbra.listener.line6":
        "Against it: see torn page. And if you have not found that page — "
        "stop. Completely. Right now.",

    "journal.penumbra.originel.title": "the Origin",
    "journal.penumbra.originel.subtitle": "sightings: three in four years",
    "journal.penumbra.originel.faded":
        "…three times in four years. I am not writing what it did the third…",
    "journal.penumbra.originel.line1":
        "Three times. In four years. That is all I have, and it is already "
        "more than writing it down was worth.",
    "journal.penumbra.originel.line2":
        "The others imitate. This one does not. I am convinced — without "
        "proof — that the others are imitating something, and that it is this.",
    "journal.penumbra.originel.line3":
        "It was not at the edge of the light. It was in the middle of it. In "
        "broad daylight, the second time.",
    "journal.penumbra.originel.line4":
        "It did nothing. None of the three times. I think it came to see how "
        "far along this book was.",
    "journal.penumbra.originel.line5":
        "Against it: I have nothing. Four years, and I have nothing to write "
        "here. It is the only line in this survey I am ashamed of.",

    "journal.penumbra.method_camera.title": "The Flash",
    "journal.penumbra.method_camera.subtitle": "torn page — first of four",
    "journal.penumbra.method_camera.faded":
        "The flash. Well framed, no glass, no rain. It cannot bear being seen clearly.",
    "journal.penumbra.method_camera.hint":
        "I left it where I looked the furthest. Up on the boards, with the "
        "cracked lens.",
    "journal.penumbra.method_camera.line1":
        "The camera is the best weapon in this country, and it kills nothing. "
        "That is why it works.",
    "journal.penumbra.method_camera.line2":
        "A well-framed flash, close range, no glass or obstacle between you "
        "and it: the Absent loses the face it borrowed for several seconds. "
        "It cannot help it. It is mechanical.",
    "journal.penumbra.method_camera.line3":
        "The print keeps the trace. Develop it and you will see two shapes "
        "overlaid: the one it was showing, and the other one.",
    "journal.penumbra.method_camera.line4":
        "The same flash dissolves a Semblance where it stands, without having "
        "to touch it. Prefer that: touching means leaning in, and leaning in "
        "takes time.",
    "journal.penumbra.method_camera.line5":
        "Careful: through glass, nothing. From behind, nothing. Too far, "
        "nothing. Framing is everything, and you get one attempt.",
    "journal.penumbra.method_camera.line6":
        "Always keep a charge. Always. I lost a friend because I had "
        "photographed a bird an hour earlier.",

    "journal.penumbra.method_refuge.title": "Shelters",
    "journal.penumbra.method_refuge.subtitle": "torn page — second of four",
    "journal.penumbra.method_refuge.faded":
        "It copies where you sleep. Count the windows before going in.",
    "journal.penumbra.method_refuge.hint":
        "I left it in the house that isn't straight. You will know the one: "
        "the paces come out wrong.",
    "journal.penumbra.method_refuge.line1":
        "What you build will be copied. Not stolen, not destroyed: copied. A "
        "second time, somewhere nearby.",
    "journal.penumbra.method_refuge.line2":
        "The copy is good but never exact. One window too many, a stair one "
        "step short, a door on the wrong side.",
    "journal.penumbra.method_refuge.line3":
        "Count your windows. Write the number on the door. It is ridiculous "
        "and it is what stopped me sleeping in the wrong one three times.",
    "journal.penumbra.method_refuge.line4":
        "The more you return to the same shelter, the more it degrades: a "
        "chest that rings false, an impossible torch, a photograph you did "
        "not take.",
    "journal.penumbra.method_refuge.line5":
        "None of it is real and none of it is permanent. Go out, wait, come "
        "back: your house will be yours again.",

    "journal.penumbra.method_stillness.title": "Stillness",
    "journal.penumbra.method_stillness.subtitle": "torn page — third of four",
    "journal.penumbra.method_stillness.faded":
        "Stop moving entirely. Not almost. Not slowly. Entirely.",
    "journal.penumbra.method_stillness.hint":
        "I left it at the ash circle. That place had to be good for something.",
    "journal.penumbra.method_stillness.line1":
        "Against the Listener there is only one thing, and it is hard for a "
        "reason I had not foreseen: it asks for no courage, it asks you to "
        "have none.",
    "journal.penumbra.method_stillness.line2":
        "Stop. Completely. Not almost, not slowly, not crouch-and-back-away. "
        "Still. You become inaudible, and inaudible means invisible to it.",
    "journal.penumbra.method_stillness.line3":
        "It will pass within a pace of you. It will stop. It will sweep its "
        "head left and right for what will feel like a very long time. Then "
        "it will go elsewhere.",
    "journal.penumbra.method_stillness.line4":
        "Moving crouched only carries three paces. It is the only way to move "
        "while it is near.",
    "journal.penumbra.method_stillness.line5":
        "And if you absolutely must run: break a stone far off first. It will "
        "go and search there. That is not cunning, it is all it knows how to do.",

    "journal.penumbra.last_page.title": "The Last Page",
    "journal.penumbra.last_page.subtitle": "torn page — fourth of four",
    "journal.penumbra.last_page.faded":
        "It did not come for me. I went to it.",
    "journal.penumbra.last_page.hint":
        "That one I threw down the well. I was not certain I wanted it read.",
    "journal.penumbra.last_page.line1":
        "Fourth year. I have nothing left to survey. I have been all the way round.",
    "journal.penumbra.last_page.line2":
        "I have kept the fire lit for three years so someone would find me. "
        "Nobody came. I think the smoke does not leave this place.",
    "journal.penumbra.last_page.line3":
        "So I have stopped waiting. Tomorrow I go down the well, and I will "
        "see how far it goes. It is the only line of the survey I could not fill.",
    "journal.penumbra.last_page.line4":
        "If you are reading this, you went down too. So you already know what "
        "I found, and this book teaches you nothing you cannot see for yourself.",
    "journal.penumbra.last_page.line5":
        "One last thing, and it is the only one I never verified: I do not "
        "think these things mean us harm. I think they are trying to learn to "
        "be us, and going about it badly.",
    "journal.penumbra.last_page.line6":
        "Continue the survey. Write it better than I did.   — E. W.",
}


def merge(path: pathlib.Path, additions: dict) -> None:
    data = json.loads(path.read_text(encoding="utf-8"),
                      object_pairs_hook=collections.OrderedDict)
    for key, value in additions.items():
        data[key] = value
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")
    print(f"  {path.name} -> {len(data)} clés")


def main() -> None:
    if set(FR) != set(EN):
        raise SystemExit(
            "FR/EN désynchronisés : "
            f"FR seulement={sorted(set(FR) - set(EN))}, "
            f"EN seulement={sorted(set(EN) - set(FR))}")
    print(f"{len(FR)} clés à injecter :")
    merge(LANG / "fr_fr.json", FR)
    merge(LANG / "fr_ch.json", FR)
    merge(LANG / "en_us.json", EN)


if __name__ == "__main__":
    main()
