#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>


//from https://www.geeksforgeeks.org/xor-cipher/
void encryptDecrypt(char inpString[100]) {
    // Define XOR key
    // Any character value will work
    char xorKey = '!';

    // calculate length of input string
    int len = strlen(inpString);

    // perform XOR operation of key
    // with every caracter in string
    for (int i = 0; i < len; i++)
    {
        inpString[i] = inpString[i] ^ xorKey;
    }
}

int validateInput(char filename[100]) {
    int len = strlen(filename);
    char safeFilename[len + 4];
    char ch;
    int count = 0;


    for (int i = 0; i < len; i++) {
        ch = filename[i];
        //validate input for filename
        if (isalnum(ch)) {
            safeFilename[count] = ch;
            count++;
        }
    }

    safeFilename[count] = '.';
    safeFilename[count + 1] = 't';
    safeFilename[count + 2] = 'x';
    safeFilename[count + 3] = 't';
    if (strcmp(safeFilename, ".txt") == 0){
        strlcpy(safeFilename, "passfile.txt", 12);
    }

    strlcpy(filename, safeFilename, len + 5);

    return 0;
}

int CreateSaferPassword(char filename[100], char password[100]) { //buffer much larger than we need

    FILE *passFile;
    //printf("about to validate input \n");
    //validate input

    //printf("about to open file in safefile's name\n");
    char safeFilename[100];
    strlcpy(safeFilename, filename, 100);
    validateInput(safeFilename);

    //printf(safeFilename); //flawfinder: ignore
    //printf("\n");
    passFile = fopen(safeFilename, "wb+");

    //encrypt password so people can't just find it or change it
    //printf("about to encrypt\n");
    char encryptedPassword[100];
    strlcpy(encryptedPassword, password, 100);
    encryptDecrypt(encryptedPassword);
    //printf(encryptedPassword); //flawfinder: ignore
    //printf("\n");
   // printf("about to print password to file \n");
    fprintf(passFile, "%s", encryptedPassword);
    fclose(passFile);

    return 0;

}

int GuessSaferPassword(char filename[100], char userInput[100]) {

    FILE *passFile;

    char safeFilename[100];
    strlcpy(safeFilename, filename, 100);
    validateInput(safeFilename);
    passFile = fopen(safeFilename, "r");

    char realPassword[100];
    fgets(realPassword, 100, passFile);
    encryptDecrypt(realPassword);
    if (strcmp(userInput, realPassword) == 0) {
        printf("You guessed the password!\n");
    } else {
        printf("You did not guess the password.\n");
    }
    fclose(passFile);

    return 0;
}


/* function: GetSaferPassword
 * input: string filename
 * output: 0
 * unencrypts password, makes sure no buffer overflows will happen, uses safer function than fscanf */

int GetSaferPassword(char filename[100], char pass[100]) {

    FILE *passFile;
    char safeFilename[100];
    strlcpy(safeFilename, filename, 100);
    validateInput(safeFilename);

    passFile = fopen(safeFilename, "r");
    fgets(pass, 100, passFile);
    encryptDecrypt(pass);
    fclose(passFile);

    return 0;
}

int main () {

    char filename[20] = "normalfile.txt"; //argv[0];
    char evilfilename[20] = "../evilfile.txt";
    char password0[20] = "%p.%p.%p";
    char password1[20] = "password1"; //argv[1];
    char password2[20] = "password2"; //argv[2];
    char password3[100] = "password3isMuchTooLongForTheVulnerableFunctionButNotTheSafeOne"; //argv[3];
    char password4[20];

    strcpy(filename, "safefile");

        printf("\nCalling CreateSaferPassword to try to print stack vars into a place we're not supposed to be...\n");
        CreateSaferPassword(evilfilename, password0);
        printf("Calling CreateSaferPassword like we're supposed to...\n");
        CreateSaferPassword(filename, password1);
        printf("Guessing correct password...");
        GuessSaferPassword(filename, password1);

        printf("Taking advantage of the race condition to change the password file...\n");

        char safeFilename[100];
        strcpy(safeFilename, filename);

        validateInput(safeFilename);
        FILE *passFile = fopen(safeFilename, "wb+");
        fprintf(passFile, "%s", password2);
        fclose(passFile);

        printf("Calling GuessSaferPassword with original password...\n");
        GuessSaferPassword(filename, password1);
        printf("Calling GuessSaferPassword with new password...\n");
        GuessSaferPassword(filename, password2);

        printf("Trying to make password that will crash the system...\n");
        CreateSaferPassword(filename, password3);
        printf("Getting unencrypted password...");
        GetSaferPassword(filename, password4);
        printf("%s", password4);

        return 0;
}