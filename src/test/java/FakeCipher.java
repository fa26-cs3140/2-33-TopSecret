public class FakeCipher implements Cipher {

    @Override
    public String decipher(String text, String keyFileName) {
        return "DECIPHERED: " + text;
    }
}