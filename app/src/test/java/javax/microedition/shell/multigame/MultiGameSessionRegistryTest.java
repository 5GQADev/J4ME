package javax.microedition.shell.multigame;

import org.junit.Test;
import static org.junit.Assert.*;

public class MultiGameSessionRegistryTest {
    @Test public void hasFourIndependentSlots() {
        MultiGameSessionRegistry registry = new MultiGameSessionRegistry();
        registry.select(0, new MultiGameSessionRegistry.GameSpec("A", "/a", null));
        registry.select(3, new MultiGameSessionRegistry.GameSpec("D", "/d", null));
        assertEquals(MultiGameSessionRegistry.State.SELECTED, registry.get(0).state);
        assertEquals(MultiGameSessionRegistry.State.EMPTY, registry.get(1).state);
        assertEquals("D", registry.get(3).game.title);
    }

    @Test public void enforcesLifecycleTransitions() {
        MultiGameSessionRegistry registry = new MultiGameSessionRegistry();
        registry.select(1, new MultiGameSessionRegistry.GameSpec("B", "/b", null));
        registry.markLoading(1);
        registry.markRunning(1);
        registry.markPaused(1);
        assertEquals(MultiGameSessionRegistry.State.PAUSED, registry.get(1).state);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsRunningWithoutLoading() {
        MultiGameSessionRegistry registry = new MultiGameSessionRegistry();
        registry.select(0, new MultiGameSessionRegistry.GameSpec("A", "/a", null));
        registry.markRunning(0);
    }

    @Test public void clearingSlotDoesNotAffectOtherSlots() {
        MultiGameSessionRegistry registry = new MultiGameSessionRegistry();
        registry.select(0, new MultiGameSessionRegistry.GameSpec("A", "/a", null));
        registry.select(2, new MultiGameSessionRegistry.GameSpec("C", "/c", null));
        registry.clear(0);
        assertEquals(MultiGameSessionRegistry.State.EMPTY, registry.get(0).state);
        assertEquals(MultiGameSessionRegistry.State.SELECTED, registry.get(2).state);
    }
}
