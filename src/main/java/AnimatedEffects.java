import java.io.PrintStream;

public class AnimatedEffects implements Effects {
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static final String[] FRAMES = {"|", "/", "-", "\\"};

    @Override
    public void banner(PrintStream out) {
        out.println(GREEN + "==============================");
        out.println("   TOP SECRET: MISSION ARCHIVE");
        out.println("==============================" + RESET);
    }

    @Override
    public void loading(PrintStream out, String message) {
        try {
            for (int i = 0; i < 12; i++) {
                out.print("\r" + message + " " + FRAMES[i % 4]);
                out.flush();
                Thread.sleep(80);
            }
            out.println("\r" + message + " done");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
