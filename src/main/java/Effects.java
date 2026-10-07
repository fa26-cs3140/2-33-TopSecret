import java.io.PrintStream;

/** Adding that visual sauce **/
public interface Effects {
    void banner(PrintStream out);
    void loading(PrintStream out, String message);

    Effects NONE = new Effects() {
        public void banner(PrintStream out) { }
        public void loading(PrintStream out, String message) { }
    };
}
