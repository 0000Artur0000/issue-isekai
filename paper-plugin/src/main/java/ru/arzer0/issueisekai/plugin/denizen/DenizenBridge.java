package ru.arzer0.issueisekai.plugin.denizen;

import com.denizenscript.denizen.Denizen;

public final class DenizenBridge {
    private static boolean registered;

    private DenizenBridge() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        Denizen denizen = Denizen.getInstance();
        if (denizen == null || !denizen.isEnabled()) {
            throw new IllegalStateException("Denizen is not enabled");
        }
        registered = true;
    }
}
