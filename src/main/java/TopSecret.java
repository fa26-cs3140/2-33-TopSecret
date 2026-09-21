/*
 * Entry point. The main program is in UserInterface so it can be tested.
 */
public class TopSecret {

    public static void main(String[] args) {
        // B: swap PlaceholderControl for your class here
        UserInterface ui = new UserInterface(new PlaceholderControl());
        System.out.print(ui.run(args));
    }
}
