package spacecolony.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionTest {
    @Test
    void none_hasNullId() {
        assertEquals(Selection.Kind.NONE, Selection.NONE.kind());
        assertNull(Selection.NONE.id());
    }

    @Test
    void factories_setKindAndId() {
        assertEquals(Selection.Kind.BODY, Selection.body("earth").kind());
        assertEquals("earth", Selection.body("earth").id());
        assertEquals(Selection.Kind.SITE, Selection.site("site-1").kind());
        assertEquals(Selection.Kind.SHIP, Selection.ship("ship-1").kind());
    }
}
