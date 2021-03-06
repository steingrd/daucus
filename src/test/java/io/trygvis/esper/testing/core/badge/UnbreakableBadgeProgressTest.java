package io.trygvis.esper.testing.core.badge;

import fj.*;
import fj.data.*;
import io.trygvis.esper.testing.*;
import io.trygvis.esper.testing.core.db.*;
import junit.framework.*;
import org.joda.time.*;

import java.util.*;
import java.util.List;

import static io.trygvis.esper.testing.Uuid.randomUuid;
import static java.util.UUID.*;

public class UnbreakableBadgeProgressTest extends TestCase {
    UUID uuid = randomUUID();

    public void testBadge() {
        BuildDto success = new BuildDto(uuid, new DateTime(), new DateTime(), true, null);
        BuildDto failure = new BuildDto(uuid, new DateTime(), new DateTime(), false, null);

        Uuid person = randomUuid();

        UnbreakableBadgeProgress p = UnbreakableBadgeProgress.initial(person);

        List<UnbreakableBadge> badges = new ArrayList<>();

        for (int i = 0; i < 55; i++) {
            P2<UnbreakableBadgeProgress, Option<UnbreakableBadge>> p2 = p.onBuild(success);

            if (i < 50) {
                assertEquals(i, p.builds.size());
            }

            if (i == 50) {
                assertEquals(0, p.builds.size());
            }

            if (p2._2().isSome()) {
                badges.add(p2._2().some());
            }

            p = p2._1();
        }

        assertEquals(5, p.builds.size());
        assertEquals(3, badges.size());
        assertEquals(1, badges.get(0).level);
        assertEquals(10, badges.get(0).builds.size());
        assertEquals(2, badges.get(1).level);
        assertEquals(20, badges.get(1).builds.size());
        assertEquals(3, badges.get(2).level);
        assertEquals(50, badges.get(2).builds.size());

        P2<UnbreakableBadgeProgress, Option<UnbreakableBadge>> p2 = p.onBuild(failure);

        assertEquals(0, p2._1().builds.size());
        assertFalse(p2._2().isSome());
    }
}
