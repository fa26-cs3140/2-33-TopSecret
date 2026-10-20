import java.util.List;

public interface CredentialStore {
    boolean exists();
        List<Credential> readAll() throws CredentialException;
        void writeAll(List<Credential> credentials) throws CredentialException;
}
