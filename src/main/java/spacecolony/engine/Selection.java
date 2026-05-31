package spacecolony.engine;

/**
 * Current player selection. {@code id} identifies the selected entity within {@code type}'s
 * domain; {@code id} is null when {@code type} is NONE.
 */
public record Selection(Kind kind, String id) {
    public enum Kind { NONE, BODY, SITE, SHIP }

    public static final Selection NONE = new Selection(Kind.NONE, null);

    public static Selection body(String bodyId) { return new Selection(Kind.BODY, bodyId); }
    public static Selection site(String siteId) { return new Selection(Kind.SITE, siteId); }
    public static Selection ship(String shipId) { return new Selection(Kind.SHIP, shipId); }
}
