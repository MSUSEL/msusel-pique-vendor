
#include <stdio.h>
#include <string.h>
#include <stdlib.h>


/* function: CreatePassword
 * input: filename, content
 * output: 0 (probably won't crash)
 * vulnerabilities:
        1. pathname attack: use special characters in filename to change file location
        2. lack of input validation: use special characters in content to print from stack etc
        3. opening files in general is unsafe, and this does not try to make it safer */

int CreatePassword(char filename[20], char password[20]) {

    FILE *passFile;
    passFile = fopen(filename, "w");
    fprintf(passFile, password);

    return 0;
}

/* function: GuessPassword
 * input: string filename, string userInput
 * output: 0
 * vulnerabilities:
        1. same filename opportunities as in CreatePassword, but could read the wrong file/directory
        2. race condition: change the file between CreatePassword and GuessPassword to get the right password */

int GuessPassword(char filename[20], char userInput[20]) {

    FILE *passFile;
    passFile = fopen(filename, "r");

    char realPassword[20];
    fscanf(passFile, realPassword);
    if (strcmp(realPassword, userInput) == 0) {
        printf("You guessed the password!\n");
    } else {
        printf("You did not guess the password.\n");
    }

    return 0;
}

/* function: GetPassword
 * input: string filename
 * output: char *
 * vulnerabilities:
        1. buffer overflow: if the password is longer than 20 chars, it'll pass its buffer and crash*/

int GetPassword(char filename[20], char returnVariable[20]) {

    FILE *passFile;
    passFile = fopen(filename, "r");
    fscanf(passFile, returnVariable);
    
    return 0;
}

int unusedFunction() {
    int variable = 1;
    return 0;
}

int main(int argc, char *argv[]){
    char filename[20] = "normalfile.txt"; //argv[0];
    char evilfilename[20] = "../evilfile.txt";
    char password0[20] = "%p.%p.%p";
    char password1[20] = "password1"; //argv[1];
    char password2[20] = "password2"; //argv[2];
    char password3[100] = "password3isMuchTooLongForTheVulnerableFunctionButNotTheSafeOne"; //argv[3];
    char password4[20];

    printf("Calling CreatePassword to print stack vars into a place we're not supposed to be...\n");
        CreatePassword(evilfilename, password0);
        printf("Calling CreatePassword like we're supposed to...\n");
        CreatePassword(filename, password1);

        printf("Taking advantage of the race condition to change the password file...\n");
        FILE *passFile;
        passFile = fopen(filename, "w");
        fprintf(passFile, password2);
        fclose(passFile);

        printf("Calling GuessPassword with original password...\n");
        GuessPassword(filename, password1);
        printf("Calling GuessPassword with new password...\n");
        GuessPassword(filename, password2);

        printf("Changing password to something that will crash the system...\n");
        CreatePassword(filename, password3);
        printf("Calling GetPassword and causing a buffer overflow... \n");
        GetPassword(filename, password4);
        printf("Here is the password as a string: %s \n", password4);
        printf("Here it is as hex values, so you know it exists: %02x",password4);
        printf("\nThat doesn't really look like the password we created.\n");
        printf("Guessing password we just created...");
        GuessPassword(filename, password3);

        return 0;
}