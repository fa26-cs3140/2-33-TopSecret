import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class MenuDemo {
    public static void main(String[] args) {
        MissionRepository repo = () -> List.of(
                new Mission(1, "Operation Sandtrap",
                        "Bug the diplomatic pouch during the layover in Rome.", LocalDate.of(1970, 11, 3)),
                new Mission(2, "The Warsaw Whisper",
                        "Intercept correspondence from the underground press.", LocalDate.of(1975, 8, 12)));
        FileHandler fileHandler = new FileHandlerImpl();
        Cipher cipher = new SubstitutionCipher(fileHandler);
        ProgramControl control = new ProgramControlImpl(
                fileHandler, cipher, new MissionSearchService(repo), repo);
        new UserInterface(control, new AnimatedEffects())
                .runMenu(new Scanner(System.in), System.out);
    }
}
