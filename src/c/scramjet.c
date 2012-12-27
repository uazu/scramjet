/*
 * Copyright (c) 2011-2012 Jim Peters, http://uazu.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Scramjet: Make Java fast enough for command-line programs by
 * keeping a JVM running in the background.  Like Nailgun but runs a
 * JVM for each user, locally, so avoiding security problems.
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdarg.h>
#include <unistd.h>
#include <errno.h>
#include <ctype.h>
#include <glob.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <time.h>
#include <fcntl.h>
#include <termios.h>
#include <poll.h>

#define DEBUG 0
#define DEBUG_MESSAGES 0

/* Stuff to configure alternative app-name and config directories. */
#ifndef APP_NAME
#define APP_NAME "scramjet"
#endif
#ifndef JVM_NAME
#define JVM_NAME "JVM"
#endif
#ifndef JVM_START_EXAMPLE
#define JVM_START_EXAMPLE "java -jar scramjet.jar"
#endif

typedef unsigned int uint;
typedef unsigned char uchar;

void
debug(char *fmt, ...) {
   va_list ap; va_start(ap, fmt);
   vfprintf(stderr, fmt, ap);
   fprintf(stderr, "\n");
}

void
warn(char *fmt, ...) {
   va_list ap; va_start(ap, fmt);
   vfprintf(stderr, fmt, ap);
   fprintf(stderr, "\n");
}

// Error with errno string
void
errorE(char *fmt, ...) {
   va_list ap; va_start(ap, fmt);
   vfprintf(stderr, fmt, ap);
   fprintf(stderr, "\n  %s\n", strerror(errno));
   exit(1);
}

void
error(char *fmt, ...) {
   va_list ap; va_start(ap, fmt);
   vfprintf(stderr, fmt, ap);
   fprintf(stderr, "\n");
   exit(1);
}

#define NL "\n"

void
usage() {
   error(
      "Usage:"
      NL "  " APP_NAME " -S or --start             (start " JVM_NAME " manually)"
      NL "  " APP_NAME " -K or --stop              (stop " JVM_NAME ")"
      NL "  " APP_NAME " -s or --status            (check " JVM_NAME " status)"
      NL "  " APP_NAME " [opt] <classname> <args>  (run Tool <classname> with <args>)"
      NL "  " APP_NAME " [opt] <alias> <args>      (run Tool that has given alias)"
      NL "  sj-<alias> <args>    (where sj-<alias> is a link to " APP_NAME " binary)"
      NL "  <alias> <args>       (where <alias> is a link to " APP_NAME " binary)"
      NL ""
      NL "Options:"
      NL "  -j <jar-or-folder>   (ensure JAR or folder is in classpath)"
      NL "  -R            (restart JVM before running tool, useful after rebuilding)"
      NL ""
      NL "Builtins:     (aliases to net.uazu.scramjet.tool.*)"
      NL "  sj-classpath                  (show classpath)"
      NL "  sj-alias                      (list aliases)"
      NL "  sj-alias <alias> <classname>  (add an alias)"
      NL "  sj-threads [-l]               (list running threads, -l: with backtraces)"
      NL ""
      NL "Configuration in ." APP_NAME "/config:"
      NL "  #...                       (comment)"
      NL "  startup <command-line>     (" JVM_NAME " startup, example: " JVM_START_EXAMPLE ")"
#ifndef SCRAMJET_ECLIPSE
      NL "  idle_timeout <minutes>     (shutdown after N mins of inactivity, default 15)"
#endif
      NL "  alias <alias> <classname>  (set up <alias> as alias for <classname>)"
      NL "  classpath <jar-or-folder>  (add a JAR or folder to the classpath)"
      NL "  charset <charset>          (terminal charset, default ISO-8859-1"
      NL "                              see java.nio.charset.Charset.forName())"
   );
}

void *
Alloc(size_t len) {
   void *p= calloc(1, len);
   if (!p) error("Out of memory");
   return p;
}

#define ALLOC(type) ((type*) Alloc(sizeof(type)))

char *
StrDup(char *str) {
   char *p = strdup(str);
   if (!p) error("Out of memory");
   return p;
}   

// Temporary buffer for messages
int tmpbuf_len;
int tmpbuf_rd;
int tmpbuf_wr;
char *tmpbuf;

// Current input/output streams
FILE *out_pipe;
int in_fd = -1;
char inbuf[1024];
int in_off;
int in_len;
int using_proxy;   // Proxy in use

int stdin_eof;  // Hit EOF?
char stdinbuf[1024];

// Pipe used internally to flag signals
int signal_pipe[2];

void 
init_tmpbuf() {
   if (!tmpbuf) {
      tmpbuf_rd = 0;
      tmpbuf_wr = 0;
      tmpbuf_len = 1024;
      tmpbuf = Alloc(tmpbuf_len);
   }
}

void
clear_tmpbuf() {
   init_tmpbuf();
   tmpbuf_rd = 0;
   tmpbuf_wr = 0;
}

void
mksp(int total) {
   int newlen = tmpbuf_len;
   char *newbuf;
   while (newlen <= total) newlen *= 2;
   newbuf = Alloc(newlen);
   memcpy(newbuf, tmpbuf, tmpbuf_len);
   free(tmpbuf);
   tmpbuf = newbuf;
   tmpbuf_len = newlen;
}

void
put(int val) {
   if (tmpbuf_wr >= tmpbuf_len)
      mksp(tmpbuf_wr+1);
   tmpbuf[tmpbuf_wr++] = val;
}

void 
put_int(uint val) {
   int a;
   for (a = 28; a>0; a-=7) {
      if ((val >> a) != 0) {
         for (; a>0; a-=7)
            put(128 | (127 & (val>>a)));
         put(127 & val);
         return;
      }
   }
   put(127 & val);
}

void
put_tail(char *data, int len) {
   int a;
   for (a = 0; a<len; a++)
      put(data[a]);
}

void
put_data(char *data, int len) {
   int a;
   put_int((uint) len);
   for (a = 0; a<len; a++)
      put(data[a]);
}

void
put_str(char *str) {
   put_data(str, strlen(str));
}

void
putf(char *fmt, ...) {
   va_list ap;
   while (1) {
      va_start(ap, fmt);
      int rv = vsnprintf(
         tmpbuf + tmpbuf_wr, tmpbuf_len - tmpbuf_wr, fmt, ap);
      if (tmpbuf_wr + rv < tmpbuf_len) {
         tmpbuf_wr += rv;
         put(0);
         tmpbuf_wr--;
         return;
      }
      mksp(tmpbuf_wr + rv + 1);
      va_end(ap);
   }
}

/**
 * Flush the output to the pipe.
 */
void
write_flush() {
   if (0 != fflush(out_pipe))
      errorE("Unable to write to named pipe:");
}

/**
 * Write a complete message to the currently selected output.  Format
 * string contains %i for encoded-integer (int), %s for encoded-string
 * (char*), %r for encoded raw data (char*, int), and %t for data at
 * the end of the message (no need to store length).  Note that output
 * stream is not flushed.  Use write_flush() for that.
 */
void
write_msg(char *fmt, ...) {
   va_list ap; va_start(ap, fmt);
   char *p, ch;

   clear_tmpbuf();
   tmpbuf_wr += 8;
   
   p = fmt;
   while (*p) {
      if (*p != '%') {
         put(*p++);
      } else {
         p++;
         ch = *p++;
         if (ch == 'i') {
            put_int(va_arg(ap, uint));
         } else if (ch == 't') {
            char *data = va_arg(ap, char*);
            int len = va_arg(ap, int);
            put_tail(data, len);
         } else if (ch == 's') {
            put_str(va_arg(ap, char*));
         } else if (ch == 'r') {
            char *data = va_arg(ap, char*);
            int len = va_arg(ap, int);
            put_data(data, len);
         } else {
            put(ch);
         }
      }
   }

   {
      int msglen = tmpbuf_wr - 8;
      int hdrlen;
      char *packet;
      int packet_len;
      int rv;
      
      tmpbuf_wr = 0;
      put_int(msglen);
      hdrlen = tmpbuf_wr;
      packet = tmpbuf + 8 - hdrlen;
      memcpy(packet, tmpbuf, hdrlen);
      packet_len = hdrlen + msglen;

      if (DEBUG_MESSAGES) {
         int a;
         fprintf(stderr, "Writing message: ");
         for (a = 0; a<packet_len; a++) {
            int ch = packet[a] & 255;
            if (ch >= 32 && ch <= 126) {
               fputc(ch, stderr);
               if (ch == '\\')
                  fputc(ch, stderr);
            } else {
               fprintf(stderr, "\\x%02X", ch);
            }
         }
         fprintf(stderr, "\n");
      }

      while (packet_len > 0) {
         rv = fwrite(packet, sizeof(char), packet_len, out_pipe);
         if (rv > 0) {
            packet += rv;
            packet_len -= rv;
            continue;
         }
         if (ferror(out_pipe))
            errorE("Write error on named pipe:");
      }
   }
}

int
inbuf_get() {
   if (in_off < in_len)
      return (int) (uchar) inbuf[in_off++];

   in_off = 0;
   in_len = 0;

   while (1) {
      int rv = read(in_fd, inbuf, sizeof(inbuf));
      if (rv > 0) {
         in_len = rv;
         return inbuf_get();
      }
      if (rv == 0) error("End of file on input named pipe");
      if (errno == EINTR)
         continue;
      errorE("Read error on named pipe:");
   }
}
   
/**
 * Read a complete message into tmpbuf.  Blocks until one is
 * available.
 */
void
read_msg() {
   int a;
   int val = 0;
   while (1) {
      int ch = inbuf_get();
      val = (val << 7) | (ch & 127);
      if (ch & 128) continue;
      break;
   }
   clear_tmpbuf();
   for (a = val; a>0; a--)
      put(inbuf_get());

   if (DEBUG_MESSAGES) {
      int a;
      fprintf(stderr, "Read message: ");
      for (a = 0; a<tmpbuf_wr; a++) {
         int ch = tmpbuf[a] & 255;
         if (ch >= 32 && ch <= 126) {
            fputc(ch, stderr);
            if (ch == '\\')
               fputc(ch, stderr);
         } else {
            fprintf(stderr, "\\x%02X", ch);
         }
      }
      fprintf(stderr, "\n");
   }
}

/**
 * Get a character from tmpbuf_rd, or -1 if EOD.
 */
int
get(int *err) {
   if (tmpbuf_rd >= tmpbuf_wr) {
      *err = 1;
      return -1;
   }
   return tmpbuf[tmpbuf_rd++];
}

int
get_int(int *err) {
   int val = 0;
   while (1) {
      int ch = get(err);
      if (ch < 0) return -1;
      val = (val << 7) | (ch & 127);
      if (ch & 128) continue;
      break;
   }
   return val;
}

char *
get_data(int *err, int *lenp) {
   int a;
   int len = get_int(err);
   if (*err) return NULL;
   char *rv = Alloc(len + 1);
   rv[len] = 0;
   for (a = 0; a<len; a++) {
      rv[a] = get(err);
      if (*err) break;
   }
   if (*err) {
      free(rv);
      return NULL;
   }
   *lenp = len;
   return rv;
}

char *
get_str(int *err) {
   int dmy;
   return get_data(err, &dmy);
}

char *
get_tail(int *err, int *lenp) {
   int a;
   int len = tmpbuf_wr - tmpbuf_rd;
   char *rv = Alloc(len + 1);
   rv[len] = 0;
   for (a = 0; a<len; a++)
      rv[a] = get(err);
   *lenp = len;
   return rv;
}


/**
 * Attempt to match a format against the data in tmpbuf, filling in
 * values if found.  %i fetches an int, and requires an int* to save
 * the value in.  %s fetches a string and requires a char** to save
 * the strdup'd value in.  %r fetches raw data, and requires a
 * (char**,int*) to save the strdup'd data and length in.  %t fetches
 * raw data at the end of a message (without a length), and requires a
 * (char**,int*) to save the strdup'd data and length in.
 * 
 * @return 1 match success, values saved (strdup'd values must be
 * freed), else 0 match failure, values nulled
 */
int 
match_msg(char *fmt, ...) {
   int pass;
   for (pass = 0; pass<2; pass++) {
      va_list ap; 
      char *p = fmt;
      int err = 0;

      va_start(ap, fmt);
      tmpbuf_rd = 0;
      
      while (*p) {
         char ch = *p++;
         if (ch != '%') {
            if (get(&err) != ch)
               err = 1;
         } else {
            ch = *p++;
            if (ch == 'i') {
               int *ip = va_arg(ap, int*);
               if (!err) *ip = get_int(&err);
               if (err) *ip = 0;
            } else if (ch == 't') {
               char **cp = va_arg(ap, char**);
               int *ip = va_arg(ap, int*);
               *cp = NULL;
               if (!err) *cp = get_tail(&err, ip);
               if (err || pass == 0) {
                  free(*cp);
                  *cp = NULL;
               }
            } else if (ch == 's') {
               char **cp = va_arg(ap, char**);
               *cp = NULL;
               if (!err) *cp = get_str(&err);
               if (err || pass == 0) {
                  free(*cp);
                  *cp = NULL;
               }
            } else if (ch == 'r') {
               char **cp = va_arg(ap, char**);
               int *ip = va_arg(ap, int*);
               *cp = NULL;
               if (!err) *cp = get_data(&err, ip);
               if (err || pass == 0) {
                  free(*cp);
                  *cp = NULL;
                  *ip = 0;
               }
            } else {
               err = 1;
            }
         }
      }
      va_end(ap);
      
      if (tmpbuf_rd != tmpbuf_wr)
         err = 1;

      if (err)
         return 0;
   }
   return 1;
}

typedef struct Alias Alias;
struct Alias {
   Alias *nxt;
   char *alias;
};
typedef struct ClassPath ClassPath;
struct ClassPath {
   ClassPath *nxt;
   char *path;
};
  
// Config
char *startup_cmd = NULL;
Alias *aliases = NULL;
ClassPath *classpaths = NULL;
int idle_timeout = 15;

#define MIN_FREE_PROXIES 4

/**
 * Writes a dot-dir filename into tmpbuf.
 */
void 
dot_dir_fnam(char *fnam) {
   clear_tmpbuf();
   putf("%s/." APP_NAME "/%s", getenv("HOME"), fnam);
}

/**
 * Load up config (trashes inbuf).  Only used when starting server.
 * After that server remembers stuff.
 */
void
load_config() {
   int idle_timeout_set = 0;

   if (startup_cmd)
      error("load_config() run twice");

   dot_dir_fnam("config");
   
   FILE *in = fopen(tmpbuf, "r");
   if (!in) error("Can't open config file: %s", tmpbuf);

   while (NULL != fgets(inbuf, sizeof(inbuf), in)) {
      // Strip LF and trailing spaces, and join lines ending with backslash
      while (1) {
         char *lf = strchr(inbuf, '\n');
         if (lf) {
            *lf = 0;
            while (lf > inbuf && isspace(lf[-1])) *--lf = 0;
            if (lf > inbuf && lf[-1] == '\\') {
               *--lf = 0;
               fgets(lf, inbuf + sizeof(inbuf) - lf, in);
               continue;
            }
         }
         break;
      }

      if (inbuf[0] == '#') continue;
      if (inbuf[0] == 0) continue;

      if (0 == memcmp(inbuf, "alias ", 6)) {
         Alias *alias = ALLOC(Alias);
         alias->nxt = aliases;
         alias->alias = StrDup(inbuf+6);
         aliases = alias;
         continue;
      }
      if (0 == memcmp(inbuf, "classpath ", 10)) {
         ClassPath *cp = ALLOC(ClassPath);
         cp->nxt = classpaths;
         cp->path = StrDup(inbuf+10);
         classpaths = cp;
         continue;
      }
      if (0 == memcmp(inbuf, "startup ", 8)) {
         if (startup_cmd)
            error("Error: more than one 'startup' line specified in config");
         startup_cmd = StrDup(inbuf+8);
         continue;
      }
      if (0 == memcmp(inbuf, "idle_timeout ", 13)) {
         char dmy;
         if (idle_timeout_set)
            error("Error: more than one 'idle_timeout' line specified in config");
         if (1 != sscanf(inbuf+13, "%d %c", &idle_timeout, &dmy))
            error("Invalid idle_timeout line: %s", inbuf);
         idle_timeout_set = 1;
         continue;
      }
      error("Bad config line: %s", inbuf);
   }

   fclose(in);

   if (!startup_cmd)
      error("No 'startup' command specified in config file");
}

int 
file_exists(char *fnam) {
   struct stat sb;
   return 0 == stat(fnam, &sb);
}

/**
 * Scan the .scramjet/terminfo file to see if we have a line for the
 * present TERM.  If not found, adds a line for TERM and rescans.
 * Returns string (reference into inbuf) or NULL if not found.
 */
static char *scan_terminfo(int retry) {
   FILE *in;
   dot_dir_fnam("terminfo");
   in = fopen(tmpbuf, "r");
   if (!in) {
      FILE *out = fopen(tmpbuf, "w");
      if (!out)
         error("Can't create file: %s", tmpbuf);
      fprintf(out, "# Cached details extracted from terminfo\n");
      fclose(out);
   } else {
      char *TERM = getenv("TERM");
      int TERM_len;
      if (!TERM)
         error("TERM environment variable not set");
      TERM_len = strlen(TERM);
      while (NULL != fgets(inbuf, sizeof(inbuf), in)) {
         if (0 == memcmp(inbuf, TERM, TERM_len) && inbuf[TERM_len] == ' ') {
            fclose(in);
            return inbuf + TERM_len;
         }
      }
      fclose(in);
   }

   if (retry)
      return NULL;

   /* Not cached: Add a line for TERM.  Overhead of calling out
    * insignificant as done only once then cached */
   {
      int rv = system("echo $TERM `infocmp -1 | grep 'colors#'` >>$HOME/." APP_NAME "/terminfo");
      if (0 != WEXITSTATUS(rv))
         error("Calling infocmp to get terminfo details for TERM failed");
   }
   return scan_terminfo(1);
}

/**
 * Set up the SCRAMJET_IS_256_COLOR environment variable if not yet
 * set.
 */
void
setup_env_256_color() {
   int is_256_color = -1;
   /* Override order: SCRAMJET_IS_256_COLOR overrides
    * .scramjet/terminfo[$TERM], overrides infocmp lookup */
   if (!getenv("SCRAMJET_IS_256_COLOR")) {
      /* Detect via terminfo cache if not specified */
      char *terminfo = scan_terminfo(0);
      if (terminfo) {
         char *colors = strstr(terminfo, "colors#");
         if (colors) {
            int val = atoi(colors + 7);
            is_256_color = val >= 256;
         }
      }
      if (is_256_color < 0) {
         error("Error: Unable to get 'colors#' from terminfo.  "
               "Override by doing 'echo \"$TERM colors#8\" >>~/." APP_NAME "/terminfo' or "
               "setting SCRAMJET_IS_256_COLOR in environment to 0 or 1.");
      }
      if (is_256_color)
         putenv("SCRAMJET_IS_256_COLOR=1");
      else 
         putenv("SCRAMJET_IS_256_COLOR=0");
   }
}   

/**
 * Create FIFOs and 'owner' flag for the given proxy.  Owner flag is
 * created first to stop anyone else trying to connect to this proxy
 * until the Java end of it is up and running.
 */
void
create_proxy(int a) {
   int fd;
   dot_dir_fnam("");
   putf("%d-owner", a);
   if (-1 == (fd = creat(tmpbuf, 0600)))
      errorE("Failed to create owner flag: %s", tmpbuf);
   if (0 != close(fd))
      errorE("Failed creating owner flag: %s", tmpbuf);

   dot_dir_fnam("");
   putf("%d-in", a);
   if (0 != mkfifo(tmpbuf, 0600))
      errorE("Failed to create named pipe: %s", tmpbuf);
   
   dot_dir_fnam("");
   putf("%d-out", a);
   if (0 != mkfifo(tmpbuf, 0600))
      errorE("Failed to create named pipe: %s", tmpbuf);
}

/**
 * Allocate a proxy for sole use of this process.  Sets up out_pipe.
 * The input pipe is not set up right away as the Java process only
 * opens it once a command is run.
 */
void
grab_proxy() {
   int pid = getpid();
   int owner;
   int a;
   int free = 0;
   int n_proxies = 0;
   
   in_fd = -1;
   out_pipe = NULL;
   for (a = 0; 1; a++) {
      dot_dir_fnam("");
      putf("%d-in", a);
      if (!file_exists(tmpbuf)) {
         n_proxies = a;
         break;
      }
      dot_dir_fnam("");
      putf("%d-owner", a);
      if (file_exists(tmpbuf))
         continue;
      if (out_pipe) {
         free++;
         continue;
      }
      // Append our PID to owner file
      FILE *app = fopen(tmpbuf, "a");
      fprintf(app, "%d\n", pid);
      fclose(app);
      // Read it back.  If our PID is first, we won the race, if there was a race.
      FILE *in = fopen(tmpbuf, "r");
      owner = -1;
      fscanf(in, "%d", &owner);
      fclose(in);
      if (owner == pid) {
         using_proxy = a;
         dot_dir_fnam("");
         putf("%d-in", a);
         out_pipe = fopen(tmpbuf, "w");
         if (!out_pipe)
            errorE("Unable to open pipe for writing: %s", tmpbuf);
         continue;
      }
   }
   if (!out_pipe)
      error("All proxies are in use");

   // Add new proxies if there are less than MIN_FREE_PROXIES free
   int inuse = n_proxies - free;
   while (inuse + MIN_FREE_PROXIES > n_proxies) {
      int ii = n_proxies++;
      create_proxy(ii);
      write_msg("new_proxy %i", ii);
   }
}

void
setup_in_fd() {
   dot_dir_fnam("");
   putf("%d-out", using_proxy);
   in_fd = open(tmpbuf, O_RDONLY);
   if (in_fd < 0)
      errorE("Unable to open pipe for reading: %s", tmpbuf);
}

/**
 * Release the proxy.
 */
void
release_proxy() {
   if (in_fd >= 0)
      close(in_fd);
   if (out_pipe)
      fclose(out_pipe);
   in_fd = -1;
   out_pipe = NULL;
}

/**
 * Test whether server is running.  Returns: 0 running, 1 nothing
 * running with that PID, 2 invalid PID-file, 3 no PID-file,
 */
int
server_not_running() {
   dot_dir_fnam("server.pid");

   int pid;
   FILE* in = fopen(tmpbuf, "r");
   if (!in)
      return 3;

   if (1 != fscanf(in, "%d", &pid)) {
      fclose(in);
      return 2;
   }
   fclose(in);

   if (kill(pid, 0) < 0 && errno == ESRCH)
      return 1;

   return 0;
}

void
sleep_ms(int ms) {
   struct timespec ts;
   ts.tv_sec = ms/1000;
   ts.tv_nsec = (ms%1000) * 1000000;
   nanosleep(&ts, NULL);
}

/**
 * Check whether server is running, display and return exit status
 * accordingly.
 */
void
status() {
   if (server_not_running()) {
      printf(JVM_NAME " not running\n");
      exit(1);
   }
   printf(JVM_NAME " running\n");
   exit(0);
}

/**
 * Write out classpaths.
 */
void
write_classpaths() {
   while (classpaths) {
      ClassPath *cp = classpaths;
      classpaths = cp->nxt;
      write_msg("classpath %s", cp->path);
      free(cp->path);
      free(cp);
   }
}

/**
 * Start server if it is not already running.
 * Returns: 1 proxy still grabbed, 0 no proxy connection
 */
int 
start_server(int verbose, int keep_open) {
   if (!server_not_running())
      return 0;

   if (verbose) {
#ifdef SCRAMJET_ECLIPSE
      printf("(If Eclipse starts up fully but startup doesn't complete below, check the\n");
      printf(" net.uazu.scramjet plugin is installed in the Eclipse dropins folder.)\n\n");
#endif
      printf("Starting " JVM_NAME " ... ");
      fflush(stdout);
   }

   // Load config
   load_config();

   // Delete all files from last server run
   {
      int a;
      glob_t globbuf;
      dot_dir_fnam("*.pid");
      glob(tmpbuf, 0, NULL, &globbuf);
      dot_dir_fnam("*-in");
      glob(tmpbuf, GLOB_APPEND, NULL, &globbuf);
      dot_dir_fnam("*-out");
      glob(tmpbuf, GLOB_APPEND, NULL, &globbuf);
      dot_dir_fnam("*-owner");
      glob(tmpbuf, GLOB_APPEND, NULL, &globbuf);
      
      if (globbuf.gl_pathv)
         for (a = 0; globbuf.gl_pathv[a]; a++)
            unlink(globbuf.gl_pathv[a]);
      
      globfree(&globbuf);
   }

   // Create proxy FIFOs for slot 0
   create_proxy(0);

   // Start JVM
   {
      clear_tmpbuf();
      putf("echo $$ >~/." APP_NAME "/server.pid && exec %s", startup_cmd);

      // Open /dev/null write-only so that reading gives an error
      int devnull = open("/dev/null", O_WRONLY);
      if (devnull < 0)
         errorE("Failed to open /dev/null");

      int rv = fork();
      if (rv < 0)
         errorE("Failed to fork to start JVM:");
      if (rv == 0) {
         // Redirect stdin/out/err to /dev/null
         dup2(devnull, 0);
         dup2(devnull, 1);
         dup2(devnull, 2);
         close(devnull);

         // Ignore SIGHUP and SIGINT
         signal(SIGHUP, SIG_IGN);
         signal(SIGINT, SIG_IGN);

         if (execl("/bin/sh", "/bin/sh", "-c", tmpbuf, NULL) < 0)
            errorE("Failed to run JVM via /bin/sh:\n  /bin/sh -c %s", tmpbuf);
      }
      close(devnull);
   }

   // Wait for JVM to start up and delete "0-owner" flag
   {
      int waited = 0;
      dot_dir_fnam("0-owner");
      while (file_exists(tmpbuf)) {
         if (waited > 10000)
            error("Scramjet server did not start up after 10 seconds");
         sleep_ms(100);
         waited += 100;
      }
   }

   // Try to talk to JVM to setup alias/classpath.  Also
   // grab_proxy() adds the other initial proxies.
   {
      Alias *ap;
      ClassPath *cp;
      grab_proxy();

      write_msg("idle_timeout %i", idle_timeout);
      for (ap = aliases; ap; ap= ap->nxt)
         write_msg("alias %s", ap->alias);
      write_classpaths();
      write_flush();
      if (!keep_open)
         release_proxy();
   }

   if (verbose)
      printf("DONE\n");

   return keep_open;
}

/**
 * Stop server if it is running.
 */
void 
stop_server() {
   if (server_not_running())
      return;

   printf("Stopping " JVM_NAME " ... ");
   fflush(stdout);

   grab_proxy();
   write_msg("shutdown");
   release_proxy();

   int cnt = 0;
   while (!server_not_running()) {
      if (cnt > 5000)
         error("Scramjet server did not respond to shutdown after 5 seconds");
      sleep_ms(100);
      cnt += 100;
   }

   printf("DONE\n");
   fflush(stdout);
}

void
write_data(int fd, char *data, int len) {
   while (len > 0) {
      int cnt = write(fd, data, len);
      if (cnt >= 0) {
         data += cnt;
         len -= cnt;
         continue;
      }
      if (errno == EINTR)
         continue;
      errorE("Write error on fd %d:", fd);
   }
}

// ------------------------------------------------------------------------
// CONSOLE handling
//

static struct termios tsave;         // Terminal save-state
static int stdin_init = 0;

static void term_stdin() {
   if (stdin_init) {
      if (-1 == tcsetattr(0, TCSANOW, &tsave))
         warn("Can't restore terminal settings: %s", strerror(errno));
      stdin_init = 0;
   }
}

static void init_stdin() {
   struct termios tbuf;

   if (stdin_init)
      return;

   if (!isatty(0))
      error("Input is not a terminal");

   if (-1 == tcgetattr(0, &tbuf))
      errorE("Can't get terminal attributes:");

   tsave= tbuf;
   cfmakeraw(&tbuf);

   if (-1 == tcsetattr(0, TCSANOW, &tbuf))
      errorE("Can't set terminal attributes:");

   stdin_init = 1;
}

int con_qinit = 0;
char *con_cleanup;
int con_cleanup_len;

void
con_term() {
   term_stdin();
   write_data(1, con_cleanup, con_cleanup_len);
   con_cleanup_len = 0;
}

// Signal handler
static void
console_resized(int sig) {
   if (1 != write(signal_pipe[1], "W", 1))
      errorE("Failed to write flag to internal signal_pipe:");
}
   
void 
con_init() {
   struct sigaction sa;

   if (con_qinit) return;

   con_qinit = 1;
   con_cleanup = StrDup("");

   sa.sa_flags = 0;
   sa.sa_handler = console_resized;

   if (0 != sigaction(SIGWINCH, &sa, NULL)) 
      errorE("Can't set up SIGWINCH handler:");
   
   atexit(con_term);
}

void 
con_send_win_size() {
   struct winsize size;
   
   if (ioctl(0, TIOCGWINSZ, &size))
      errorE("Can't read terminal size:");

   write_msg("con-size %i %i", size.ws_col, size.ws_row);
   write_flush();
}

/**
 * Process con-* messages
 */
void 
con_process_msg() {
   char *tmp;
   int tmplen;
   
   con_init();
   
   // "con-raw-on"           # Console: put stdin in raw mode
   if (match_msg("con-raw-on")) {
      init_stdin();
      return;
   }
   
   // "con-raw-off"          # Console: restore stdin
   if (match_msg("con-raw-off")) {
      term_stdin();
      return;
   }
   
   // "con-cleanup %t"       # Console: set cleanup string (dumped to STDOUT at exit)
   if (match_msg("con-cleanup %t", &tmp, &tmplen)) {
      free(con_cleanup);
      con_cleanup = tmp;
      con_cleanup_len = tmplen;
      return;
   }
   
   // "con-req-size"         # Console: request window size
   if (match_msg("con-req-size")) {
      con_send_win_size();
      return;
   }
   
   // "con-term"             # Console: do final cleanup manually (done atexit anyway)
   if (match_msg("con-term")) {
      con_term();
      return;
   }
   
   warn("Bad con-* message: %s", &tmpbuf[0]);
}

// ------------------------------------------------------------------------
// MAIN
//

/**
 * Process a message
 */
void
process_msg() {
   char *data;
   int len;
   int status;

   // "1%t"                  # Data to output on stdout
   if (match_msg("1%t", &data, &len)) {
      write_data(1, data, len);
      return;
   }
      
   // "2%t"                  # Data to output on stderr
   if (match_msg("2%t", &data, &len)) {
      write_data(2, data, len);
      return;
   }
      
   // "exit %i"              # Exit with status
   if (match_msg("exit %i", &status)) {
      release_proxy();
      exit(status);
      return;
   }

   // "run %s"               # Run an external app and wait for it to complete
   if (match_msg("run %s", &data)) {
      int rv = system(data);
      int err = rv == -1;
      int exited = !err && WIFEXITED(rv);
      int signalled = !err && WIFSIGNALED(rv);
      int intquit = signalled && (WTERMSIG(rv) == SIGINT || WTERMSIG(rv) == SIGQUIT);
      write_msg("run-status %i %i",
                err ? -1 : exited ? 0 : intquit ? 1 : signalled ? 2 : 3,
                err ? errno : exited ? WEXITSTATUS(rv) : signalled ? WTERMSIG(rv) : 0);
      write_flush();
      free(data);
      return;
   }

   // con-*
   if (0 == memcmp(tmpbuf, "con-", 4)) {
      con_process_msg();
      return;
   }

   warn("Invalid message received:");
   {
      int a;
      for (a = 0; a<tmpbuf_wr; a++) {
         int ch = tmpbuf[a] & 255;
         if (ch == '\\')
            fprintf(stderr, "\\\\");
         else if (ch >= 32 && ch <= 126)
            fputc(ch, stderr);
         else
            fprintf(stderr, "\\x%02X", ch);
      }
      fprintf(stderr, "\n");
   }
}

extern char **environ;

int
main(int ac, char **av) {
   char **pp;
   char *cmd = strrchr(av[0], '/');
   char **args;
   cmd = cmd ? cmd+1 : av[0];
   ac--; av++;

   // Invoked without using an alias hard link
   if (0 == strcmp(cmd, APP_NAME)) {
      // Local options
      if (ac == 1) {
         if (0 == strcmp(av[0], "--stop") || 0 == strcmp(av[0], "-K")) {
            stop_server();
            return 0;
         }
         if (0 == strcmp(av[0], "--start") || 0 == strcmp(av[0], "-S")) {
            start_server(1, 0);
            return 0;
         }
         if (0 == strcmp(av[0], "--status") || 0 == strcmp(av[0], "-s")) {
            status();
            return 0;
         }
      }

      // Other options
      while (ac > 0) {
         if (ac >= 2 && 0 == strcmp(av[0], "-j")) {
            ClassPath *cp = ALLOC(ClassPath);
            cp->nxt = classpaths;
            cp->path = StrDup(av[1]);
            classpaths = cp;
            ac-=2; av+=2;
            continue;
         }
         if (0 == strcmp(av[0], "-R")) {
            stop_server();
            ac--; av++;
            continue;
         }
         break;
      }

      // Get alias/class from command-line
      if (ac == 0 || ac > 0 && av[0][0] == '-')
         usage();
      cmd = *av++;
   } else {
      // Strip "sj-" off front of hard-link alias name if present
      if (0 == memcmp(cmd, "sj-", 3))
         cmd += 3;
   }

   setup_env_256_color();
   args = av;
      
   // Start server if not running
   int open = start_server(0, 1);

   // Grab a proxy and start code running
   if (!open)
      grab_proxy();
   write_classpaths();
   while (*args)
      write_msg("arg %s", *args++);

   for (pp = environ; *pp; pp++)
      write_msg("env %s", *pp);

   if (!getcwd(inbuf, sizeof(inbuf)))
      error("Current working directory too long for inbuf[]");
   write_msg("cwd %s", inbuf);
   write_msg("run %s", cmd);
   write_flush();

   setup_in_fd();
   if (0 != pipe(signal_pipe))
      errorE("Unable to create internal pipe:");

   // Main loop
   while (1) {
      struct pollfd pfd[3];
      pfd[0].fd = in_fd;
      pfd[0].events = POLLIN;
      pfd[1].fd = signal_pipe[0];
      pfd[1].events = POLLIN;
      pfd[2].fd = 0;
      pfd[2].events = POLLIN;

      int rv = poll(pfd, stdin_eof ? 2 : 3, -1);
      if (rv < 0 && errno != EINTR)
         errorE("Call to poll failed:");
      
      // STDIN
      if (!stdin_eof) {
         if (pfd[2].revents & (POLLERR | POLLNVAL))
            error("Error on STDIN");
         if (pfd[2].revents & POLLHUP) {
            stdin_eof = 1;
            write_msg("EOF");
            write_flush();
         }
         if (pfd[2].revents & POLLIN) {
            int cnt = read(0, stdinbuf, sizeof(stdinbuf));
            if (cnt == 0) {
               stdin_eof = 1;
               write_msg("EOF");
               write_flush();
            } else if (cnt < 0) {
               errorE("STDIN error:");
            } else {
               write_msg("0%t", stdinbuf, cnt);
               write_flush();
            }
         }
      }
      
      // Signal pipe (for notification of signals received)
      if (pfd[1].revents & (POLLERR | POLLHUP | POLLNVAL))
         error("Unexpected error on signal_pipe");
      if (pfd[1].revents & POLLIN) {
         char buf[16];
         int cnt = read(signal_pipe[0], buf, sizeof(buf));
         int sigwinch = 0;
         int a;
         for (a = 0; a<cnt; a++) {
            if (buf[a] == 'W')
               sigwinch = 1;
            else
               error("Unexpected flag in signal_pipe: %c", buf[a]);
         }
         if (sigwinch)
            con_send_win_size();
      }

      // Remote pipe receiving from JVM
      if (pfd[0].revents & POLLHUP)
         error("Java process hung up pipe");
      if (pfd[0].revents & (POLLERR | POLLNVAL))
         error("Error on incoming pipe");
      if (pfd[0].revents & POLLIN) {
         // For really big messages may block until the entire message
         // has been transmitted
         while (1) {
            read_msg();
            process_msg();
            if (in_off >= in_len)
               break;
         }
      }
   }
   return 0;
}
         
// END //
