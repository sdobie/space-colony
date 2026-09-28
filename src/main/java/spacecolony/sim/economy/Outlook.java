package spacecolony.sim.economy;

/** Where a stock is heading at its current net rate. */
public sealed interface Outlook {
    int MAX_DAYS = 999;
    /** Nets smaller than this per day count as steady. */
    double STEADY = 0.005;

    record Steady() implements Outlook {}
    record Empty() implements Outlook {}
    record Full() implements Outlook {}
    record EmptyIn(int days) implements Outlook {}
    record FullIn(int days) implements Outlook {}

    static Outlook of(double stock, double cap, double net) {
        if (stock <= 1e-6 && net <= STEADY) return new Empty();
        if (stock >= cap - 1e-6 && net >= -STEADY) return new Full();
        if (Math.abs(net) < STEADY) return new Steady();
        double days = net < 0 ? stock / -net : (cap - stock) / net;
        int d = (int) Math.min(MAX_DAYS, Math.ceil(days - 1e-9));
        return net < 0 ? new EmptyIn(Math.max(1, d)) : new FullIn(Math.max(1, d));
    }
}
