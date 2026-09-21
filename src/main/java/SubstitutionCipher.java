import java.util.HashMap;
import java.util.Map;

public class SubstitutionCipher implements Cipher {
    private final FileHandler fileHandler;

    public SubstitutionCipher(FileHandler fileHandler) {
        if (fileHandler == null) {
            throw new IllegalArgumentException("fileHandler cannot be null");
        }
        this.fileHandler = fileHandler;
    }

    @Override
    public String decipher(String text, String keyFileName) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        String keyContent = fileHandler.readKey(keyFileName);
        if (keyContent == null) {
            throw new InvalidCipherException("Key file could not be read: " + keyFileName);
        }
        Map<Character, Character> map = buildDecipherMap(keyContent);

        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(map.getOrDefault(c, c));
        }
        return out.toString();
    }

    private static Map<Character, Character> buildDecipherMap(String keyContent) {
        String normalized = keyContent.replace("\r", "");
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        String[] lines = normalized.split("\n", -1);
        if (lines.length != 2) {
            throw new InvalidCipherException("Key must have exactly two lines.");
        }
        String plain = lines[0];
        String coded = lines[1];
        if (plain.isEmpty()) {
            throw new InvalidCipherException("Key lines must not be empty.");
        }
        if (plain.length() != coded.length()) {
            throw new InvalidCipherException("Key lines must be the same length.");
        }

        Map<Character, Character> map = new HashMap<>();
        for (int i = 0; i < plain.length(); i++) {
            if (plain.indexOf(plain.charAt(i)) != i) {
                throw new InvalidCipherException("Duplicate character in first key line: '" + plain.charAt(i) + "'");
            }
            if (coded.indexOf(coded.charAt(i)) != i) {
                throw new InvalidCipherException("Duplicate character in second key line: '" + coded.charAt(i) + "'");
            }
            map.put(coded.charAt(i), plain.charAt(i));
        }
        return map;
    }
}