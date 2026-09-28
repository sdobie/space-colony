package spacecolony.sim.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutlookTest {
    @Test
    void emptyAndFull() {
        assertEquals(new Outlook.Empty(), Outlook.of(0, 1000, -1));
        assertEquals(new Outlook.Full(), Outlook.of(1000, 1000, 1));
    }

    @Test
    void daysRoundUp() {
        assertEquals(new Outlook.EmptyIn(100), Outlook.of(100, 1000, -1));
        assertEquals(new Outlook.EmptyIn(334), Outlook.of(100, 1000, -0.3));
        assertEquals(new Outlook.FullIn(50), Outlook.of(900, 1000, 2));
    }

    @Test
    void tinyNetsAreSteady() {
        assertEquals(new Outlook.Steady(), Outlook.of(500, 1000, 0.004));
        assertEquals(new Outlook.Steady(), Outlook.of(500, 1000, -0.0001));
    }

    @Test
    void daysCapAt999() {
        assertEquals(new Outlook.EmptyIn(999), Outlook.of(999_999, 10_000_000, -0.01));
    }
}
