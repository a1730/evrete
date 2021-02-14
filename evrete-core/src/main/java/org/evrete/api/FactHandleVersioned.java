package org.evrete.api;

import java.io.Serializable;

public class FactHandleVersioned implements Serializable {
    private int version;
    private FactHandle handle;


    public FactHandleVersioned(FactHandle handle, int version) {
        this.handle = handle;
        this.version = version;
    }

    public FactHandleVersioned(FactHandle handle) {
        this(handle, 0);
    }

    public int getVersion() {
        return version;
    }

    public FactHandle getHandle() {
        return handle;
    }

    @Override
    public String toString() {
        return "{version=" + version +
                ", handle=" + handle +
                '}';
    }
}