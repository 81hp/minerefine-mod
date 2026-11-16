package minerefinehud.server;

/**
 * Whether this connection is to MineRefine: its name has been seen on it.
 *
 * Off after joining until the sidebar or tab list names the server, which takes a moment. Once on,
 * it stays on until the player leaves or joins again. The name being gone later says nothing:
 * MineRefine's sidebar is not always its usual one (escorting the Halloween caravan is thought to
 * swap it out), and a mod that went quiet then would miss boss broadcasts and lose its panels in
 * the middle of the game.
 *
 * No Minecraft types.
 */
// UNVERIFIED: that going from MineRefine to another Minehut server through the proxy counts as a
// new join, as the boss and drop readers have assumed all along. If it does not, the mod stays on
// there until the next real join, which is what it did everywhere before this setting.
public final class ServerGate {

    /** Volatile: leaving a server is reported from the network thread as well. */
    private volatile boolean on;

    /** A new connection: nothing seen on it yet. */
    public void reset() {
        on = false;
    }

    /** One look at the sidebar and tab list. */
    public void observe(boolean named) {
        if (named) {
            on = true;
        }
    }

    public boolean onMineRefine() {
        return on;
    }
}
