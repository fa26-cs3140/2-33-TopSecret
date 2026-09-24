Homework 3 README for Team 2-33

This program follows the Homework 3 Top Secret requirements. It lists encrypted
mission files and displays deciphered file contents from the command line.

Run the program with one of these commands:

- ./gradlew run
- java TopSecret from build/classes/java/main
- java -jar TopSecret.jar from build/libs

Run all tests with ./gradlew test.

Mission files are stored in data/. Cipher keys are stored in ciphers/.
Command-line options are documented in docs/userinterface.txt.

Project files:

- TopSecret - Starts the program.
- UserInterface - Validates command-line input and displays output.
- ProgramControl - Connects the user interface, file handler, and cipher.
- FileHandler - Safely lists and reads mission and key files.
- Cipher - Validates cipher keys and deciphers .cip file contents.

The project builds with Gradle and uses JUnit 5 for testing.
