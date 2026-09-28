package spacecolony.tutorial;

import java.util.List;
import java.util.function.Predicate;

/**
 * One tutorial step (Plan 6 §5.3).
 *
 * @param html    card body: short HTML paragraphs, with UI labels in {@code <b>}
 * @param targets component names to highlight, in priority order; the first one showing wins
 * @param hint    shown when no target is showing (e.g. "Select Earth Hub first."); may be null
 * @param done    completion test; null for a manual step, which shows a Next button
 */
public record TutorialStep(String id, String title, String html, List<String> targets, String hint,
                           Predicate<TutorialContext> done) {
    public boolean manual() { return done == null; }
}
