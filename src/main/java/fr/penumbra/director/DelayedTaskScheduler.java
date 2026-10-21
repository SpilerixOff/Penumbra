package fr.penumbra.director;

import fr.penumbra.PenumbraMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Planificateur de tâches différées du mod : une file par niveau (dimension),
 * chaque tâche est exécutée quand {@code level.getGameTime()} atteint son
 * échéance. Utilisé pour les effets en plusieurs temps (pas mimétiques,
 * restauration de torche, entrée en faux calme…).
 * Toutes les méthodes sont appelées depuis le thread serveur uniquement.
 */
public final class DelayedTaskScheduler {

    /** Tâche planifiée : échéance en ticks de jeu + action à exécuter. */
    private record ScheduledTask(long dueTick, Runnable task) {}

    /**
     * File minimale séparant explicitement le retrait des tâches et leur exécution.
     * La visibilité package permet un test unitaire sans démarrer Minecraft.
     */
    static final class TaskQueue {
        private final List<ScheduledTask> tasks = new ArrayList<>();

        void add(long dueTick, Runnable task) {
            this.tasks.add(new ScheduledTask(dueTick, task));
        }

        List<Runnable> drainDue(long now) {
            List<Runnable> due = new ArrayList<>();
            Iterator<ScheduledTask> it = this.tasks.iterator();
            while (it.hasNext()) {
                ScheduledTask scheduled = it.next();
                if (now >= scheduled.dueTick()) {
                    it.remove();
                    due.add(scheduled.task());
                }
            }
            return due;
        }

        boolean isEmpty() {
            return this.tasks.isEmpty();
        }

        int size() {
            return this.tasks.size();
        }
    }

    /**
     * Files d'attente isolées par instance serveur, puis par dimension.
     * L'identité du serveur est volontaire : deux mondes ouverts successivement dans
     * la même JVM ne doivent jamais partager des closures différées.
     */
    private static final Map<MinecraftServer, Map<ResourceKey<Level>, TaskQueue>>
            TASKS_BY_SERVER = new IdentityHashMap<>();

    private DelayedTaskScheduler() {
    }

    /**
     * Planifie l'exécution de {@code task} dans {@code delayTicks} ticks de jeu
     * du niveau donné (l'écoulement du temps est celui de cette dimension).
     */
    public static void runLater(ServerLevel level, int delayTicks, Runnable task) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(task, "task");
        long dueTick = level.getGameTime() + Math.max(0, delayTicks);
        TASKS_BY_SERVER
                .computeIfAbsent(level.getServer(), key -> new HashMap<>())
                .computeIfAbsent(level.dimension(), key -> new TaskQueue())
                .add(dueTick, task);
    }

    /**
     * Tick serveur (enregistré dans {@code PenumbraMod.onInitialize}) : exécute
     * les tâches arrivées à échéance dans chaque niveau. Une tâche en échec est
     * consignée et n'interrompt pas les suivantes.
     */
    public static void onServerTick(MinecraftServer server) {
        Map<ResourceKey<Level>, TaskQueue> serverTasks = TASKS_BY_SERVER.get(server);
        if (serverTasks == null || serverTasks.isEmpty()) {
            return;
        }

        // Phase 1 : détacher toutes les tâches dues avant d'exécuter la moindre
        // closure. Une closure peut ainsi appeler runLater(), y compris dans la même
        // dimension, sans modifier la liste actuellement parcourue.
        List<Runnable> dueTasks = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            TaskQueue queue = serverTasks.get(level.dimension());
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            dueTasks.addAll(queue.drainDue(level.getGameTime()));
            if (queue.isEmpty()) {
                serverTasks.remove(level.dimension());
            }
        }
        if (serverTasks.isEmpty()) {
            TASKS_BY_SERVER.remove(server);
        }

        // Phase 2 : exécuter le lot détaché. Toute tâche nouvellement planifiée
        // attendra au moins le prochain tick, ce qui évite aussi les boucles sans fin
        // de tâches à délai nul.
        for (Runnable task : dueTasks) {
            try {
                task.run();
            } catch (Exception e) {
                PenumbraMod.LOGGER.error("Tâche différée Penumbra en échec", e);
            }
        }
    }

    /**
     * Purge les closures d'un serveur arrêté. Une closure capture souvent un niveau,
     * un joueur ou une position et ne doit jamais survivre au cycle de vie du monde.
     */
    public static void onServerStopped(MinecraftServer server) {
        TASKS_BY_SERVER.remove(server);
    }
}
