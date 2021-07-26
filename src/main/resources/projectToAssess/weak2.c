/***************************************************************
 * weak.c: gets as many individual flawfinder hits as i can.
 *         compiles but does not run. cwe count: 16
 *         cwe-829 is windows-unique and this code was written
 *         on a mac, so it does not appear here.
 ***************************************************************/

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>


 int main (void) {

    //CWE-119!/CWE-120: buffer size too small
    char buffer[10];

    printf("input: ");

    //CWE-120 buffer overflow: doesn't check if input overflows buffer
    gets(buffer);


    seteuid(0);
    /* do some stuff */

    seteuid(getuid());

    #include <stdio.h>
    #include <stdlib.h>
    #include <unistd.h>

    int BUFFERSIZE = 512;

    void main(int argc, char** argv) {
      char ipaddr[BUFFERSIZE];
      snprintf(ipaddr, BUFFERSIZE, "ping -c 4 %s", argv[1]);
      if(setuid(0) == -1) printf("setUID ERROR");
      system(ipaddr);
    }

    //CWE-134: improper input validation: input "%p" prints numbers straight off stack
    printf(buffer);

    char *string;

    //CWE-120, CWE-20: does not limit length of string in scanf
    printf("Please enter system call: ");
    scanf(string);

    //CWE-78: does not attempt to neutralize system call
    int a = system(string);

    FILE *pointer;
    char *password = "password123";
    char *userInput;
    //CWE-362: race condition: opens file but doesn't check that the file hasn't been maliciously changed
    pointer = fopen("newfile.txt", "wb+");
    fprintf(pointer, "%s", password);
    printf("Please enter the password: ");
    //another CWE-120: flawfinder can't tell if the limit is small enough
    scanf("%20s", userInput);
    fgets(password, 20, pointer);  //this method is safer than gets()
    if (strcmp(userInput, password) == 0) {
        printf("Correct password!");
    } else {
        printf("You guessed the wrong password.");
    }
    fclose(pointer);

    //CWE-126: does not account for a string that might not be \0-terminated
    int length = strlen(password);

    char template[] = "fileXXXXXX";
    //CWE-377: insecure temporary file
    mkstemp(template);
    FILE *newtemp = fopen(template, "wb+"); //flawfinder: ignore (already hit this one)
    fclose(newtemp);

    useconds_t seconds;
    //CWE-676: use of obsolete/potentially-dangerous function
    usleep(seconds);

    //CWE-327: this prng is not random enough to be secure
    srand(1);

    //CWE-120/785!: buffer overflow that can overflow filename internally
    char *cwd = getcwd (NULL, 0);
    cwd = realpath(".", NULL);

    //CWE-190: if the stringNumber is too big, the int could wrap around or overflow
    char *stringNumber = "123456";
    int b = atoi(stringNumber);

    //CWE-250, CWE-22: with improper input, an unauthorized user can set "path" to
    // reach files and directories they shouldn't be able to reach
    char *path;
    scanf("%s", path);  //flawfinder: ignore (we've already done this one)
    chroot (path);

    //CWE-807, CWE-20: environment variables can be changed by an attacker - don't
    // trust them to be true
    char *env_var = getenv( "PATH");

    //CWE-732: gives people higher security access than we want them to have
    mode_t x;
    umask(x);


    return 0;
 }