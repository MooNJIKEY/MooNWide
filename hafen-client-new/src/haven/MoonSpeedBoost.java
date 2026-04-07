package haven;

/**
 * Sprint cycling: rapidly toggles between sprint and run.
 * Sprint phase gives burst speed, run phase recovers stamina.
 * Net effect: higher average speed than constant run with less
 * stamina drain than continuous sprinting.
 *
 * Only activates when sprint (mode 3) is available (max >= 3).
 * Automatically enables AutoDrink to keep stamina up.
 */
public class MoonSpeedBoost {
    private static double lastToggle = 0;
    private static boolean sprintPhase = false;
    private static double lastSpeedMultResend = 0;

    public static void tick(GameUI gui, double dt) {
        if (!MoonConfig.speedBoost) return;
        if (gui == null) return;

        Speedget sg = findSpeedget(gui);
        if (sg == null || sg.max < 3) return;

        int stam = MoonAutoDrink.getCurrentStamina(gui);
        double now = Utils.rtime();
        double elapsed = now - lastToggle;

        double sprintDur = sprintDuration(stam);
        double recoverDur = recoverDuration(stam);

        if (sprintPhase) {
            if (elapsed >= sprintDur) {
                sg.set(2);
                sprintPhase = false;
                lastToggle = now;
            }
        } else {
            if (elapsed >= recoverDur) {
                sg.set(3);
                sprintPhase = true;
                lastToggle = now;
            }
        }

        if (!MoonConfig.autoDrink)
            MoonConfig.setAutoDrink(true);
        if (MoonConfig.autoDrinkThreshold < 40)
            MoonConfig.setAutoDrinkThreshold(40);
    }

    private static double sprintDuration(int stam) {
        if (stam < 0) return 0.35;
        if (stam > 80) return 0.55;
        if (stam > 60) return 0.40;
        if (stam > 40) return 0.25;
        return 0.10;
    }

    private static double recoverDuration(int stam) {
        if (stam < 0) return 0.15;
        if (stam > 80) return 0.08;
        if (stam > 60) return 0.12;
        if (stam > 40) return 0.18;
        return 0.30;
    }

    public static Speedget findSpeedget(GameUI gui) {
        if (gui == null)
            return null;
        for (Widget w = gui.child; w != null; w = w.next) {
            Speedget sg = findSpeedgetInTree(w, 8);
            if (sg != null)
                return sg;
        }
        return null;
    }

    private static Speedget findSpeedgetInTree(Widget w, int depth) {
        if (w == null || depth < 0)
            return null;
        if (w instanceof Speedget)
            return (Speedget) w;
        for (Widget c = w.child; c != null; c = c.next) {
            Speedget sg = findSpeedgetInTree(c, depth - 1);
            if (sg != null)
                return sg;
        }
        return null;
    }

    /**
     * Re-sends current {@link Speedget} tier so outgoing hooks (lift / mult / wire multiplier) apply
     * without a manual click — same substitution as a player click on the speed bar.
     */
    public static void pingSpeedgetWire(Widget anchor) {
        if (anchor == null)
            return;
        GameUI gui = anchor.getparent(GameUI.class);
        if (gui == null)
            return;
        Speedget sg = findSpeedget(gui);
        if (sg != null)
            sg.set(sg.cur);
    }

    /**
     * Periodic {@code set(cur)} while multiplier {@code > 1} so the server keeps receiving bumped
     * {@link Speedget} tiers (interval {@link MoonConfig#speedMultResendIntervalSec}; {@code 0} = off).
     * Skipped while {@link MoonConfig#speedBoost} is on (that path already issues frequent {@code set}s).
     */
    public static void tickSpeedMultResend(GameUI gui, double now) {
        if (gui == null)
            return;
        if (!MoonConfig.experimentalSpeedWireAssist)
            return;
        double iv = MoonConfig.speedMultResendIntervalSec;
        if (iv <= 0 || MoonConfig.speedMultiplier <= 1.0 + 1e-6)
            return;
        if (MoonConfig.speedBoost)
            return;
        if (now - lastSpeedMultResend < iv)
            return;
        Speedget sg = findSpeedget(gui);
        if (sg == null || sg.cur < 0)
            return;
        lastSpeedMultResend = now;
        sg.set(sg.cur);
    }
}
