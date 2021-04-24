package org.evrete.api;

public enum KeyMode {

    OLD_OLD(false), // Known key, known facts
    NEW_NEW(true), // New key, new Facts
    OLD_NEW(true) // Known key, new facts
    ;

    private final boolean delta;

    static {
        if (OLD_OLD.ordinal() != 0) {
            throw new IllegalStateException("There is contract that the " + OLD_OLD + " key storage always comes first");
        }
    }

    public boolean isDelta() {
        return delta;
    }

    KeyMode(boolean delta) {
        this.delta = delta;
    }
}
