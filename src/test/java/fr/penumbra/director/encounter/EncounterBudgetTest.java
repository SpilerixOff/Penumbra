package fr.penumbra.director.encounter;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EncounterBudgetTest {
    @Test
    void failedPlacementChecksNeverSpendTheOwnersCooldown() {
        EncounterBudget budget = new EncounterBudget();
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < 50; i++) assertTrue(budget.canBegin(owner, 100));
        assertTrue(budget.started(owner, 100));
        assertFalse(budget.canBegin(owner, 101));
    }

    @Test
    void onlyEightOwnersCanHaveAnActorAndFinishingOneDoesNotClearAnother() {
        EncounterBudget budget = new EncounterBudget();
        UUID first = UUID.randomUUID();
        assertTrue(budget.started(first, 0));
        for (int i = 1; i < EncounterBudget.MAX_ACTIVE; i++) {
            assertTrue(budget.started(UUID.randomUUID(), 0));
        }
        UUID ninth = UUID.randomUUID();
        assertFalse(budget.started(ninth, 0));
        budget.finished(first);
        assertTrue(budget.started(ninth, 0));
        assertFalse(budget.started(first, 1800), "An elapsed cooldown cannot bypass the global actor cap");
    }

    @Test
    void disconnectCannotBypassCooldownButClockRollbackCannotLockOutForever() {
        EncounterBudget budget = new EncounterBudget();
        UUID owner = UUID.randomUUID();
        assertTrue(budget.started(owner, 100));
        budget.finished(owner);
        assertFalse(budget.canBegin(owner, 1899));
        assertTrue(budget.canBegin(owner, 1900));
        assertTrue(budget.started(owner, 1900));
        budget.finished(owner);
        assertTrue(budget.canBegin(owner, 100));
    }

    @Test
    void cooldownStorageIsBoundedAndExpiredEntriesRestoreCapacity() {
        EncounterBudget budget = new EncounterBudget();
        for (int i = 0; i < EncounterBudget.MAX_COOLDOWNS; i++) {
            UUID owner = new UUID(0, i);
            assertTrue(budget.started(owner, 0));
            budget.finished(owner);
        }
        UUID newcomer = new UUID(1, 0);
        assertFalse(budget.canBegin(newcomer, 1));
        assertTrue(budget.canBegin(newcomer, EncounterBudget.COOLDOWN_TICKS));
    }
}
