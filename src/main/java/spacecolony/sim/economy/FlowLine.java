package spacecolony.sim.economy;

import spacecolony.sim.Resource;

/**
 * One movement of one resource. {@code amount} is signed: + made or received, − used or sent.
 * {@code wanted} has the same sign and is what the source would have moved with full power
 * and full inputs.
 */
public record FlowLine(FlowSource source, Resource resource, double amount, double wanted) {
    public boolean shortfall() {
        return Math.abs(wanted) - Math.abs(amount) > 1e-9;
    }
}
