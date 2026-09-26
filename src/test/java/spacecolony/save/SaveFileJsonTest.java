package spacecolony.save;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveFileJsonTest {
    @Test
    void toJson_fromJson_isStable() throws Exception {
        World w = WorldGenerator.generate(3L);
        Simulator sim = new Simulator();
        for (int i = 0; i < 300; i++) sim.advance(w);
        String a = SaveFile.toJson(w);
        String b = SaveFile.toJson(SaveFile.fromJson(a));
        assertEquals(a, b);
    }

    @Test
    void fromJson_wrongVersion_throws() {
        String bad = SaveFile.toJson(WorldGenerator.generate(1L))
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99");
        IncompatibleSaveException e = assertThrows(IncompatibleSaveException.class, () -> SaveFile.fromJson(bad));
        assertEquals(99, e.fileSchemaVersion);
    }
}
