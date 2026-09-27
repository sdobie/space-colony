package spacecolony.tutorial;

import java.util.List;

/** Where the player is in the script, and the rules for moving on (Plan 6 §5.5). */
public final class TutorialProgress {
    private final List<TutorialStep> steps;
    private int index;

    public TutorialProgress(List<TutorialStep> steps) {
        if (steps.isEmpty()) throw new IllegalArgumentException("no steps");
        this.steps = List.copyOf(steps);
    }

    public int index() { return index; }
    public int size() { return steps.size(); }
    public TutorialStep current() { return steps.get(index); }
    public boolean onLastStep() { return index == steps.size() - 1; }

    /**
     * Advances past every consecutive automatic step whose test passes, so a player who worked
     * ahead skips what they've already done. Returns true if the step changed.
     */
    public boolean update(TutorialContext c) {
        int before = index;
        while (!onLastStep() && !current().manual() && current().done().test(c)) index++;
        return index != before;
    }

    /** Next on a manual step. */
    public void next() {
        if (!current().manual()) throw new IllegalStateException(current().id() + " is not manual");
        if (!onLastStep()) index++;
    }

    /** Skip step: always moves on, except past the last step. */
    public void skip() {
        if (!onLastStep()) index++;
    }
}
