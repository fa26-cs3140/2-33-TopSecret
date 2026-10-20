import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.ArrayList;
import java.util.List;

public class FileCredentialStore implements CredentialStore {
    static final String DEFAULT_CREDENTIAL_PATH = "data/credentials.cip";
    static final String DEFAULT_KEY_NAME = "key.txt";

    private final Path credentialPath;
    private final FileHandler fileHandler;
    private final Cipher cipher;
    private final String keyName;

    public FileCredentialStore(FileHandler fileHandler, Cipher cipher) {
        this(defaultCredentialPath(), fileHandler, cipher, DEFAULT_KEY_NAME);
    }

    FileCredentialStore(Path credentialPath, FileHandler fileHandler, Cipher cipher, String keyName) {
        this.credentialPath = Objects.requireNonNull(credentialPath, "credentialPath cannot be null");
        this.fileHandler = Objects.requireNonNull(fileHandler, "fileHandler cannot be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher cannot be null");
        this.keyName = Objects.requireNonNull(keyName, "keyName cannot be null");
    }

    @Override
    public boolean exists() {
        return Files.isRegularFile(credentialPath, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public List<Credential> readAll() throws CredentialException {
        String ciphered = readCipheredFile();
        String plain;
        try {
            plain = cipher.decipher(ciphered, keyName);
        } catch (RuntimeException e) {
            throw new CredentialException("Credential file could not be deciphered.", e);
        }

        List<Credential> credentials = new ArrayList<>();
        for (String line : plain.replace("\r", "").split("\n")) {
            if (line.isEmpty()) {
                continue;
            }

            String[] fields = line.split("\t", -1);
            if (fields.length != 2 || fields[0].isEmpty()) {
                throw new CredentialException("Credential file is invalid.");
            }
            credentials.add(new Credential(fields[0], fields[1]));
        }

        return credentials;
    }

    @Override
    public void writeAll(List<Credential> credentials) throws CredentialException {
        Objects.requireNonNull(credentials, "credentials cannot be null.");
        StringBuilder plain = new StringBuilder();
        for (Credential credential : credentials) {
            plain.append(credential.getUsername()).append("\t")
                    .append(credential.getPassword()).append("\n");
        }
        String ciphered = encipher(plain.toString());
        try {
            Path parent = credentialPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(credentialPath, ciphered, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            throw new CredentialException("Credential file could not be written.", e);
        }
    }

    private String readCipheredFile() throws CredentialException {
        if (!exists()) {
            throw new CredentialException("Credential file was not found.");
        }
        try {
            return Files.readString(credentialPath, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw new CredentialException("Credential file could not be read.", exception);
        }
    }

    private String encipher(String plain) throws CredentialException {
        String keyContent = fileHandler.readKey(keyName);
        if (keyContent == null) {
            throw new CredentialException("Credential cipher key could not be read.");
        }
        Map<Character, Character> map = buildEncipherMap(keyContent);
        StringBuilder out = new StringBuilder(plain.length());
        for (char c : plain.toCharArray()) {
            out.append(map.getOrDefault(c, c));
        }
        return out.toString();
    }

    private static Map<Character, Character> buildEncipherMap(String keyContent) throws CredentialException {
        String normalized = keyContent.replace("\r", "");
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        String[] lines = normalized.split("\n", -1);
        if (lines.length != 2 || lines[0].isEmpty() || lines[0].length() != lines[1].length()) {
            throw new CredentialException("Credential cipher key is invalid.");
        }

        Map<Character, Character> map = new HashMap<>();
        for (int i = 0; i < lines[0].length(); i++) {
            char plain = lines[0].charAt(i);
            char coded = lines[1].charAt(i);
            if (lines[0].indexOf(plain) != i || lines[1].indexOf(coded) != i) {
                throw new CredentialException("Credential cipher key is invalid.");
            }
            map.put(plain, coded);
        }
        return map;
    }

    private static Path defaultCredentialPath() {
        String override = System.getProperty("topsecret.credentials");
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim());
        }
        return Paths.get(DEFAULT_CREDENTIAL_PATH);
    }
}
