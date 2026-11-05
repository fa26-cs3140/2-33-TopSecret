package model;

import model.*;
import view.*;
import controller.*;

public class FakeCipher implements Cipher {

    @Override
    public String decipher(String text, String keyFileName) {
        return "DECIPHERED: " + text;
    }
}
