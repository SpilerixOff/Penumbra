package fr.penumbra.director;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DelayedTaskSchedulerTest {

    @Test
    void drainDetachesDueTasksWithoutExecutingThem() {
        DelayedTaskScheduler.TaskQueue queue = new DelayedTaskScheduler.TaskQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.add(10L, calls::incrementAndGet);
        queue.add(11L, calls::incrementAndGet);

        List<Runnable> due = queue.drainDue(10L);

        assertEquals(0, calls.get(), "drainDue must not execute user closures while iterating");
        assertEquals(1, due.size());
        assertEquals(1, queue.size());

        due.forEach(Runnable::run);
        assertEquals(1, calls.get());
    }

    @Test
    void zeroDelayTaskScheduledByTaskWaitsForNextDrain() {
        DelayedTaskScheduler.TaskQueue queue = new DelayedTaskScheduler.TaskQueue();
        List<String> calls = new ArrayList<>();
        queue.add(20L, () -> {
            calls.add("first");
            queue.add(20L, () -> calls.add("second"));
        });

        List<Runnable> firstBatch = queue.drainDue(20L);
        firstBatch.forEach(Runnable::run);

        assertEquals(List.of("first"), calls);
        assertEquals(1, queue.size(), "the nested task must remain queued for the next tick");

        List<Runnable> secondBatch = queue.drainDue(20L);
        secondBatch.forEach(Runnable::run);

        assertEquals(List.of("first", "second"), calls);
        assertTrue(queue.isEmpty());
    }

    @Test
    void futureTasksKeepTheirInsertionOrder() {
        DelayedTaskScheduler.TaskQueue queue = new DelayedTaskScheduler.TaskQueue();
        List<Integer> calls = new ArrayList<>();
        queue.add(30L, () -> calls.add(1));
        queue.add(40L, () -> calls.add(3));
        queue.add(30L, () -> calls.add(2));

        queue.drainDue(30L).forEach(Runnable::run);
        assertEquals(List.of(1, 2), calls);
        assertEquals(1, queue.size());

        queue.drainDue(40L).forEach(Runnable::run);
        assertEquals(List.of(1, 2, 3), calls);
        assertTrue(queue.isEmpty());
    }
}
