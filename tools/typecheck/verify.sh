#!/usr/bin/env bash
# Vérification de compilation hors ligne de Penumbra.
#
# Compile la totalité de src/main/java contre le vrai jar Minecraft 26.1.2
# (celui du loom-cache du projet) plus l'arbre de doublures de ce dossier.
# Aucun accès réseau à Maven n'est nécessaire : c'est précisément l'intérêt.
#
# Ce n'est PAS un build — voir README.md pour ce que ça ne couvre pas.
#
# Usage :  bash tools/typecheck/verify.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${TMPDIR:-/tmp}/penumbra-typecheck"
STUBS="$ROOT/tools/typecheck/stubs"
JAR="$WORK/mc-65.jar"
OUT="$WORK/classes"

mkdir -p "$WORK"

# ————————————————————————————————————————————————————————————————
# 1. Un javac récent. Le projet vise Java 25 mais le code n'utilise aucune
#    syntaxe postérieure à 21, donc un JDK 21 suffit pour le contrôle de types.
# ————————————————————————————————————————————————————————————————
JAVAC=""
for candidate in "${JAVA_HOME:-}/bin/javac" "$(command -v javac || true)" \
                 "$WORK/jdk/usr/lib/jvm/java-21-openjdk-amd64/bin/javac"; do
    [ -x "$candidate" ] && JAVAC="$candidate" && break
done

if [ -z "$JAVAC" ]; then
    echo "javac introuvable — récupération d'un JDK 21 via apt…"
    ( cd "$WORK" \
      && apt-get download openjdk-21-jdk-headless openjdk-21-jre-headless >/dev/null 2>&1 \
      && for deb in *.deb; do dpkg-deb -x "$deb" "$WORK/jdk"; done )
    JH="$WORK/jdk/usr/lib/jvm/java-21-openjdk-amd64"
    # Le paquet Ubuntu place la configuration dans /etc et la relie par lien
    # symbolique ; hors installation, le lien pend et la JVM refuse de démarrer.
    if [ -d "$WORK/jdk/etc/java-21-openjdk/security" ]; then
        rm -rf "$JH/conf/security"
        cp -r "$WORK/jdk/etc/java-21-openjdk/security" "$JH/conf/security"
    fi
    JAVAC="$JH/bin/javac"
fi
[ -x "$JAVAC" ] || { echo "impossible d'obtenir un javac"; exit 1; }
echo "javac : $("$JAVAC" -version 2>&1)"

# ————————————————————————————————————————————————————————————————
# 2. Le jar Minecraft, ramené en version de classe 65.
#    Le jar de loom est compilé en version 69 (Java 25) : un javac 21 refuse
#    de le lire. On réécrit les deux octets de version de chaque classe — la
#    lecture des signatures n'a besoin de rien d'autre.
# ————————————————————————————————————————————————————————————————
if [ ! -f "$JAR" ]; then
    SOURCE_JAR=$(find "$ROOT/.gradle/loom-cache" -name "minecraft-merged-*.jar" | head -1)
    [ -n "$SOURCE_JAR" ] || { echo "jar Minecraft introuvable dans .gradle/loom-cache"; exit 1; }
    echo "conversion du jar Minecraft (une seule fois)…"
    python3 - "$SOURCE_JAR" "$JAR" <<'PY'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
changed = 0
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_STORED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename.endswith(".class") and data[:4] == b"\xca\xfe\xba\xbe":
            if int.from_bytes(data[6:8], "big") > 65:
                data = data[:6] + (65).to_bytes(2, "big") + data[8:]
                changed += 1
        zout.writestr(item, data)
print(f"  {changed} classes ramenées en version 65")
PY
fi

# ————————————————————————————————————————————————————————————————
# 3. Compilation.
# ————————————————————————————————————————————————————————————————
rm -rf "$OUT" && mkdir -p "$OUT"
SOURCES="$WORK/sources.txt"
find "$ROOT/src/main/java" -name "*.java" > "$SOURCES"
find "$STUBS" -name "*.java" >> "$SOURCES"

MOD=$(find "$ROOT/src/main/java" -name "*.java" | wc -l)
STUB=$(find "$STUBS" -name "*.java" | wc -l)
echo "compilation : $MOD fichiers du mod + $STUB doublures"

"$JAVAC" -nowarn -proc:none -Xmaxerrs 2000 -source 21 -target 21 \
         -cp "$JAR" -d "$OUT" "@$SOURCES" 2> "$WORK/errors.txt"

COUNT=$(grep -c 'error:' "$WORK/errors.txt" || true)
if [ "$COUNT" -eq 0 ]; then
    echo "AUCUNE ERREUR — $(find "$OUT/fr" -name '*.class' | wc -l) classes produites"
else
    echo "$COUNT ERREUR(S) :"
    grep -A4 'error:' "$WORK/errors.txt" | grep -E 'error:|symbol:|location:|class file' | head -40
    exit 1
fi
