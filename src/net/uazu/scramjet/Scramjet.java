// Copyright (c) 2011-2012 Jim Peters, http://uazu.net
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//     http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.uazu.scramjet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.uazu.scramjet.nailgun.NGSecurityManager;
import net.uazu.scramjet.nailgun.ThreadLocalInputStream;
import net.uazu.scramjet.nailgun.ThreadLocalPrintStream;


/**
 * Scramjet server.  Invoked by the C scramjet binary.  Requires that
 * at least one set of FIFOs is already set up.  Expected to monitor
 * named pipes and run tool instances on behalf of the C front-end.
 *
 * <h3>Guide to the classes</h3>
 * 
 * <p>{@link Scramjet} is the main server entry point.  Once setup is
 * done, it sleeps on the main thread to handle idle shutdown.</p>
 * 
 * <p>{@link SJProxy} is a Thread that waits on a FIFO (#-in) and
 * handles running a tool and setting up input/output over the FIFOs.
 * There is one SJProxy instance per proxy connection.</p>
 * 
 * <p>{@link SJInputStream} and {@link SJOutputStream} map between
 * Java in/out streams and the messages sent over the FIFO
 * connection.</p>
 * 
 * <p>{@link MsgReader} and {@link MsgWriter} handle
 * reading/writing/formatting/parsing the messages sent over the FIFO
 * connections.</p>
 * 
 * <p>{@link Tool}: all tools should inherit from Tool.  The
 * environment of the tool is available as instance variables.  There
 * are convenience methods for printing to stdout and beeping,
 * etc.</p>
 * 
 * <p>The "nailgun" package contains nailgun-derived code to redirect
 * use of System.in/out/err and System.exit() to the tool's own
 * handlers.  It is not strictly necessary to use this, but it makes
 * it more convenient when adapting existing code.</p>
 *
 * <p>{@link SJModule} is the base-class for additional modules, and
 * package "mod" contains the implementations.  Right now only {@link
 * mod.ConsoleMod} is defined, which handles notifications of
 * window-size changes and so on.</p>
 * 
 * <p>The "con" package contains full-screen console app-related code.
 * {@link ConsoleTool} provides the framework for a console app,
 * loading up the {@link mod.ConsoleMod} and starting the event loop.
 * There are example tools under package "test".</p>
 * 
 * <p>The "eclipse" package contains Eclipse plugin related code.
 * There are example tools test.</p>
 * 
 * <p>Note that "eclipse" package is separated off because it has
 * additional dependencies beyond the core Scramjet code.</p>
 *
 * <h3>Writing tools</h3>
 *
 * <p>A simple tool must extend {@link Tool}, and implement the
 * constructor and {@link Tool#run()} method.  Arguments, environment,
 * etc are available as instance variables -- see {@link Tool}
 * javadocs.  For example:</p>
 *
 * <pre>
 * public class LongOutput extends Tool {
 *     public LongOutput(SJContext sjc) {
 *         super(sjc);
 *     }
 *     public void run() {
 *         for (int a = 0; a&lt;50000; a++)
 *             printf("%8d%8d%8d%8d%8d%8d%8d%8d%8d\n", a, a, a, a, a, a, a, a, a);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>There are more examples in the "test" package</p>
 *                                  
 */
public class Scramjet {
   /**
    * Enable debugging messages?
    */
   public static final boolean DEBUG = false;

   /**
    * Setup builtin aliases.
    */
   private static void builtin_aliases() {
      aliases.put("classpath", "net.uazu.scramjet.tool.SJClasspath");
      aliases.put("alias", "net.uazu.scramjet.tool.SJAlias");
      aliases.put("threads", "net.uazu.scramjet.tool.SJThreads");
   }
   
   /**
    * Main entry point.
    */
   public static void main(String[] args) {
      init(false);

      // Handle idle shutdown
      idle_thread = Thread.currentThread();
      while (true) {
         long now = System.currentTimeMillis();
         long last_active = 0;
         boolean all_sleeping = true;
         for (SJProxy pr : proxies) {
            if (!pr.sleeping && pr.isAlive())
               all_sleeping = false;
            if (pr.last_active > last_active)
               last_active = pr.last_active;
         }
         long timeout = last_active + 60000 * idle_timeout;
         if (timeout < now && all_sleeping)
            shutdown();
         long wait = timeout - now + 1000;
         if (wait < 0) wait = 10000;
         if (DEBUG)
            log("IDLE: all_sleeping: " + all_sleeping + ", wait: " + wait);
         try {
            Thread.sleep(wait);
         } catch (InterruptedException e) {}
      }
   }

   /**
    * Set the idle timeout in minutes.  When all the proxies have been
    * sleeping for this long waiting for a new connection, the JVM is
    * shut down.
    */
   public static void setIdleTimeout(int tmo) {
      idle_timeout = tmo;
      if (idle_thread != null)
          idle_thread.interrupt();
   }

   /**
    * Entry point for setup from Eclipse plugin.
    */
   public static void setup() {
      init(true);
   }   

   /**
    * Set up scramjet server.
    */
   private static void init(boolean plugin) {
      dotdir = new File(
         System.getenv("HOME") + File.separator +
         (plugin ? ".scramjet-eclipse" : ".scramjet"));
      
      builtin_aliases();

      // Setup logging output
      reopen_log();
      log("------------------------------------------------------------");
      log("Server start-up: " + new Date());
      
      // Set up redirections of input/output streams and exit call
      old_stdin = System.in;
      old_stdout = System.out;
      old_stderr = System.err;
      old_sm = System.getSecurityManager();
      System.setSecurityManager(new NGSecurityManager(old_sm));
      System.setIn(new ThreadLocalInputStream(System.in));
      System.setOut(new ThreadLocalPrintStream(System.out));
      System.setErr(new ThreadLocalPrintStream(System.err));

      // Log uncaught exceptions for debugging
      Thread.setDefaultUncaughtExceptionHandler(
         new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
               if (!(e instanceof SJTerminateError)) {
                  log("=== Uncaught exception on thread '" + t.getName() + "':");
                  while (e != null) {
                     e.printStackTrace(log_out);
                     e = e.getCause();
                  }
               }
            }
         });
      
      // Set up a proxy thread for first named pipe
      addProxy(0);
   }

   /**
    * Add a proxy to handle FIFOs at index 'a' if it doesn't already
    * exist.
    */
   static public void addProxy(int a) {
      synchronized(proxies) {
         if (proxies.size() > a && proxies.get(a) != null)
            return;
         
         SJProxy sjp = new SJProxy(a);
         
         while (proxies.size() <= a)
            proxies.add(null);
         proxies.set(a, sjp);
         sjp.start();
      }
   }
   
   /**
    * Restart any proxy that has died unexpectedly.  Note that this
    * shouldn't happen, but it seems that Java on receiving a SIGPIPE
    * signal may just let the thread die without throwing an
    * exception, so we have to cope with that.
    */
   public static void ensureProxiesRunning() {
      int len = proxies.size();
      for (int a = 0; a<len; a++) {
         SJProxy sjp = proxies.get(a);
         if (sjp != null && !sjp.isAlive()) {
            synchronized(proxies) {
               proxies.set(a, sjp.restart());
            }
         }
      }
   }

   /**
    * Write a message to the log output.
    */
   public static void log(String msg) {
      synchronized (log_out) {
         log_out.print(datefmt.format(new Date()));
         log_out.println(msg);
         if (++log_check >= LOG_CHECK_MAX) {
            log_check = 0;
            log_out.flush();
            if (log_file.length() >= LOG_MAX_SIZE)
               reopen_log();
         }
      }
   }

   /**
    * Get the log-file with the given number.
    */
   private static File get_log_file(int n) {
      return new File(
         System.getenv("HOME") + File.separator +
         ".scramjet" + File.separator + "log-" + n);
   }
   
   /**
    * Reopen the log file ready to write, renaming old log files if
    * current one is full.
    */
   private static void reopen_log() {
      if (log_out != null) {
         log_out.close();
         log_out = null;
      }
      
      File log0 = get_log_file(0);
      if (log0.length() >= LOG_MAX_SIZE) {
         // Rename one position back, deleting last
         for (int a = LOG_COUNT-1; a>=0; a--) {
            File curr = get_log_file(a);
            if (curr.exists()) {
               if (a+1 == LOG_COUNT) {
                  curr.delete();
               } else {
                  curr.renameTo(get_log_file(a+1));
               }
            }
         }
      }
      log_file = log0;
      try {
         log_out = new PrintStream(new FileOutputStream(log_file, true));
      } catch (FileNotFoundException e) {
         log_out = old_stderr;
         log_out.println("Can't create log file: " + log_file);
      }

      if (datefmt == null)
         datefmt = new SimpleDateFormat("yyyyMMdd-HHmmss: ");
   }

   /**
    * Stop the ScramJet server.
    */
   public static void shutdown() {
      log("Server shutdown: " + new Date());

      // Delete PID file first so that all new front-end invocations
      // immediately start up a new server, i.e. they don't attempt to
      // connect to this one which is now dying
      new File(dotdir, "server.pid").delete();
      
      System.setIn(old_stdin);
      System.setOut(old_stdout);
      System.setErr(old_stderr);
      System.setSecurityManager(old_sm);
      
      System.exit(0);
   }

   /**
    * Add a folder or JAR file to the system class path.  Reports
    * errors to log file.
    */
   public static void addClassPath(File file) {
      if (!file.exists()) {
         log("Classpath directory or JAR file does not exist: " + file);
         return;
      }
      try {
         addToSystemClassLoader(file.toURI().toURL());
      } catch (Exception e) {
         log("Unable to add classpath JAR/folder: " + e.getMessage());
      }
   }

   /**
    * Adds the specified URL representing a JAR or a folder to the
    * System ClassLoader, if it is not already in the list.
    * 
    * @param url URL of the folder or JAR to add to the System classpath.
    * @throws Exception if anything goes wrong, for example if system
    * class loader is not a URLClassLoader.
    */
   private static void addToSystemClassLoader(URL url) throws Exception {
      URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
      for (URL old : sysloader.getURLs()) {
         if (old.equals(url))
            return;
      }
      
      Class<?> sysclass = URLClassLoader.class;
      Method method = sysclass.getDeclaredMethod("addURL", new Class[] { URL.class });
      method.setAccessible(true);
      method.invoke(sysloader, new Object[] { url });
   }

   /**
    * The .scramjet/ or .scramjet-eclipse/ folder
    */
   public static File dotdir;
   
   /**
    * Proxy threads corresponding to %d-in files
    */
   private static List<SJProxy> proxies = new ArrayList<SJProxy>();
   
   /**
    * Original security manager.
    */
   private static SecurityManager old_sm;

   /**
    * Original System.in stream.
    */
   private static InputStream old_stdin;
   
   /**
    * Original System.out stream.
    */
   private static PrintStream old_stdout;
   
   /**
    * Original System.err stream.
    */
   private static PrintStream old_stderr;
   
   /**
    * Current logging output stream.
    */
   private static PrintStream log_out;

   /**
    * Date format for logging.
    */
   private static SimpleDateFormat datefmt;
   
   /**
    * Current log file
    */
   private static File log_file;
   
   /**
    * Counter for checking log file size.
    */
   private static int log_check = 0;

   /**
    * Number of strings that can be output before another size check
    * is done.
    */
   private static final int LOG_CHECK_MAX = 50;

   /**
    * Log size after which it is switched.
    */
   private static final int LOG_MAX_SIZE = 50000;

   /**
    * Log file count, i.e. maximum number of different log files kept.
    */
   private static final int LOG_COUNT = 4;
   
   /**
    * Character set for decoding/encoding strings in messages to front
    * end, and for writing to stdout/stderr.  Set according to the
    * character set detected by Java from the UNIX environment.
    */
   public static final Charset charset = Charset.defaultCharset();

   /**
    * Aliases from command names to class names.
    */
   public static Map<String,String> aliases = new HashMap<String,String>();

   /**
    * Idle-checking thread, or null
    */
   private static Thread idle_thread;
   
   /**
    * Idle timeout in minutes, default 15 minutes
    */
   private static int idle_timeout = 15;
}
      
