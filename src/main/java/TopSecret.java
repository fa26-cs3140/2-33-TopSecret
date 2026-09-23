/*
 * Entry point. The main program is in UserInterface so it can be tested.
 */
public class TopSecret {

    public static void main(String[] args) {
        FileHandler fileHandler = new FileHandlerImpl();
        Cipher cipher = new SubstitutionCipher(fileHandler);
        UserInterface ui = new UserInterface(new ProgramControlImpl(fileHandler, cipher));
        System.out.print(ui.run(args));
    }
}
