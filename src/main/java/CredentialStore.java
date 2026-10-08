public interface CredentialStore {
    boolean exists();
    Credential read() throws CredentialException;
    void write(Credential credential) throws CredentialException;
}
