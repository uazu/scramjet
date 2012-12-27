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

import static net.uazu.scramjet.Scramjet.log;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.uazu.scramjet.nailgun.NGSecurityManager;
import net.uazu.scramjet.nailgun.ThreadLocalInputStream;
import net.uazu.scramjet.nailgun.ThreadLocalPrintStream;


/**
 * SJProxy, a Thread which acts as a proxy between the C front-end and
 * the Java command running in the JVM, emulating standard in/out/err
 * and exit, and forwarding data over the pipe connections.
 *
 * <p>Handles the main loop, waiting on the input pipe for
 * instructions, then starting a command until it completes, then
 * reinitialising the pipes to start again.
 */
public class SJProxy extends Thread {
   public final int id;
   public final File in_pipe;
   public final File owner_flag;
   public final File out_pipe;
   
   /**
    * Stream for incoming messages piped from C front-end.
    */
   public FileInputStream in;

   /**
    * Stream for outgoing messages piped to C front-end.
    */
   public FileOutputStream out;

   /**
    * Class to handle writing messages to C front-end.
    */
   public MsgWriter writer;

   /**
    * Class to handle reading messages from C front-end.
    */
   public MsgReader reader;
   
   /**
    * Proxy standard input stream, which connects to C front-end
    * STDIN.
    */
   public SJInputStream stdin;

   /**
    * Proxy standard output stream, which connects to C front-end
    * STDOUT.
    */
   public PrintStream stdout;

   /**
    * Proxy standard error stream, which connects to C front-end
    * STDERR.
    */
   public PrintStream stderr;

   /**
    * Current running tool.  This is used to make sure that any old
    * threads from a previous tool's execution can't do any damage.
    */
   public Tool curr_tool;

   /**
    * List of modules active in this session.
    */
   public List<SJModule> modules = new ArrayList<SJModule>();
   
   /**
    * Has the tool terminated?
    */
   public boolean terminated;

   /**
    * Exit status of the tool.
    */
   public int exit_status;

   /**
    * Set to shut down the server on next reinit.
    */
   public boolean shutdown;

   /**
    * Are we currently sleeping waiting for a new connection?
    */
   public boolean sleeping;

   /**
    * Last time we were active before sleeping.
    */
   public long last_active = 0;

   /**
    * Status after running an external app.
    */
   public enum RunStatus {
      ERROR, EXIT, INTQUIT, SIGNAL, UNKNOWN;
   }

   /**
    * Result of running an external app.
    */
   public static class RunResult {
      /**
       * Result of running external app.
       */
      public final RunStatus status;

      /**
       * Associated value: errno for ERROR, exit status for EXIT,
       * signal number for INTQUIT or SIGNAL.
       */
      public final int value;

      public RunResult(int status, int value) {
         this.status = status == -1 ? RunStatus.ERROR :
            status == 0 ? RunStatus.EXIT :
            status == 1 ? RunStatus.INTQUIT :
            status == 2 ? RunStatus.SIGNAL : RunStatus.UNKNOWN;
         this.value = value;
      }
   }

   /**
    * Temporary result of last system() call.
    */
   private RunResult run_result;
   
   /**
    * Construct a SJProxy instance.
    */
   public SJProxy(int num) {
      super("SJProxy " + num);
      id = num;
      in_pipe = new File(Scramjet.dotdir, id + "-in");
      out_pipe = new File(Scramjet.dotdir, id + "-out");
      owner_flag = new File(Scramjet.dotdir, id + "-owner");
   }

   /**
    * Restart this proxy thread -- the caller should check first that
    * it has died with isAlive().  In theory, this should never happen
    * as all Throwables are caught, but a Java thread may die without
    * throwing an exception if SIGPIPE is raised.
    * @return The replacement, running SJProxy instance
    */
   public SJProxy restart() {
      if (Scramjet.DEBUG)
         log(id + ": Restarting zombie SJProxy which probably died due to SIGPIPE");
      
      if (out != null) {
         try { out.close(); } catch (IOException e) {}
      }
      if (in != null) {
         try { in.close(); } catch (IOException e) {}
      }      

      SJProxy sjp = new SJProxy(id);
      sjp.start();
      return sjp;
   }
         
   /**
    * Handle initial messages on a connection, either to do some
    * configuration, or to create a new context to run a command in.
    * @return SJContext or null
    */
   private SJContext
   load_context() {
      List<String> args = new ArrayList<String>();
      Map<String,String> env = new HashMap<String,String>();
      File cwd = null;
      String cmd = null;
      
      // Loop until initial messages complete, i.e. until we get a
      // 'run' command or an EOF
      while (true) {
         synchronized (reader) {
            try {
               reader.read();
            } catch (EOFException e) {
               // Expect EOF if they just make a few changes and then
               // disconnect
               return null;
            } catch (IOException e) {
               log(id + ": IOException on input pipe: " + e.getMessage());
               return null;
            }

            // Put stuff sent at every invocation first for speed
            Object[] oa;
            if (null != (oa = reader.match("arg %s"))) {
               args.add((String) oa[0]);
               continue;
            }
            if (null != (oa = reader.match("env %s"))) {
               String val = (String) oa[0];
               int ii = val.indexOf("=");
               if (ii < 0) {
                  log("Invalid environment variable spec: " + val);
               } else {
                  env.put(val.substring(0, ii), val.substring(ii+1));
               }
               continue;
            }
            if (null != (oa = reader.match("cwd %s"))) {
               cwd = new File((String) oa[0]);
               continue;
            }
            if (null != (oa = reader.match("run %s"))) {
               cmd = (String) oa[0];
               break;
            }

            // Config stuff
            if (null != (oa = reader.match("alias %s"))) {
               String alias = (String) oa[0];
               int ii = alias.indexOf("=");
               if (ii < 0) {
                  log("Invalid alias definition: " + alias);
               } else {
                  Scramjet.aliases.put(
                     alias.substring(0, ii),
                     alias.substring(ii+1));
               }
               continue;
            }
            if (null != (oa = reader.match("classpath %s"))) {
               Scramjet.addClassPath(new File((String) oa[0]));
               continue;
            }
            if (null != (oa = reader.match("new_proxy %i"))) {
               Scramjet.addProxy((Integer) oa[0]);
               continue;
            }
            if (null != (oa = reader.match("shutdown"))) {
               shutdown = true;
               continue;
            }
            if (null != (oa = reader.match("idle_timeout %i"))) {
               Scramjet.setIdleTimeout((Integer) oa[0]);
               continue;
            }
            log(id + ": Bad initial connection message: " +
                new String(reader.msg, Scramjet.charset));
         }
      }
      
      if (cmd == null)
         return null;
      
      return new SJContext(this, args.toArray(new String[0]), env, cwd, cmd);
   }      

   /**
    * Start the main loop waiting for connections.
    */
   public void run() {
     reopen:
      while (true) {
         if (Scramjet.DEBUG)
            log(id + ": Reinit proxy");
         
         // Reinitialise session.  Close pipes to reinitialise them.
         // Remove owner flag, ready for a new process to take
         // ownership and start a new session.
         if (out != null) {
            try { out.close(); } catch (IOException e) {}
            out = null;
            writer = null;
         }
         if (in != null) {
            try { in.close(); } catch (IOException e) {}
            in = null;
         }
         owner_flag.delete();
         terminated = false;
         exit_status = 0;
         curr_tool = null;
         modules.clear();

         // Handle shutdown between tool executions
         if (shutdown) {
            Scramjet.shutdown();
            return;
         }

         // This will block until a new C front-end starts and opens
         // the in-pipe to write.  We update sleeping/last_active so
         // that the Scramjet idle thread knows what is going on.
         last_active = System.currentTimeMillis();
         sleeping = true;
         try {
            in = new FileInputStream(in_pipe);
         } catch (FileNotFoundException e) {
            log("Pipe file missing: " + e);
            return;
         }
         sleeping = false;
         reader = new MsgReader(in);
         if (Scramjet.DEBUG)
            log(id + ": Input stream connect");
         
         // Quickly check that all other threads are running and
         // restart them if necessary
         Scramjet.ensureProxiesRunning();
         
         // Handle startup commands
         SJContext sjc = load_context();
         if (sjc == null) {
            if (Scramjet.DEBUG)
               log(id + ": Immediate commands complete");
            continue reopen;
         }
         if (Scramjet.DEBUG)
            log(id + ": Command context loaded");
         
         // This will block until the C front-end opens the out-pipe
         // to read
         try {
            out = new FileOutputStream(out_pipe);
         } catch (FileNotFoundException e) {
            log("Pipe file missing: " + e);
            return;
         }            
         writer = new MsgWriter(this, out);
         if (Scramjet.DEBUG)
            log(id + ": Write stream connected");

         // Setup standard streams
         try {
            stdin = new SJInputStream(this);
            stdout = new PrintStream(
               new SJOutputStream(this, "1%t"), true, Scramjet.charset.name());
            stderr = new PrintStream(
               new SJOutputStream(this, "2%t"), true, Scramjet.charset.name());
         } catch (UnsupportedEncodingException e) {
            log("Character set not recognised: " + Scramjet.charset.name());
            return;
         }
         sjc = new SJContext(sjc, stdin, stdout, stderr);

         // Look for constructor and run it
         Throwable dump = null;
         try {
            String cmd = sjc.cmd;
            String alias = Scramjet.aliases.get(cmd);
            if (alias != null) cmd = alias;
            try {
               Class<?> clas = Class.forName(cmd);
               if (!Tool.class.isAssignableFrom(clas))
                  error(sjc, "Class isn't subclass of Tool: " + cmd);
               Constructor<?> cons = clas.getConstructor(SJContext.class);
               curr_tool = (Tool) cons.newInstance(sjc);
            } catch (ClassNotFoundException e) {
               error(sjc, "Class or alias not found: " + cmd);
            } catch (NoSuchMethodException e) {
               error(sjc, "Constructor not found: new " + cmd + "(SJContext)");
            } catch (InstantiationException e) {
               error(sjc, "Cannot run an abstract class: " + cmd);
            } catch (InvocationTargetException e) {
               error(sjc, "(failure within constructor)", e.getCause());
            }
            
            // Change System.* streams and System.exit context for this
            // thread and children
            NGSecurityManager.setTool(curr_tool);
            ((ThreadLocalInputStream) System.in).init(stdin);
            ((ThreadLocalPrintStream) System.out).init(stdout);
            ((ThreadLocalPrintStream) System.err).init(stderr);

            if (Scramjet.DEBUG)
               log(id + ": Running command: " + curr_tool.cmd);
            curr_tool.run();
            
         } catch (SJTerminateError e) {
            // Okay
         } catch (Throwable t) {
            dump = t;
            exit_status = 1;
         }

         terminated = true;
         sjc.stderr.flush();
         sjc.stdout.flush();
         for (SJModule mod : modules)
            mod.cleanup();

         while (dump != null) {
            dump.printStackTrace(sjc.stderr);
            dump = dump.getCause();
         }

         if (Scramjet.DEBUG)
            log(id + ": Command exit status: " + exit_status);
         try {
            writer.write("exit %i", exit_status);
            writer.flush();
            // Sleep 100ms to allow front-end to receive message
            // before dropping connection
            Thread.sleep(100);
         } catch (SJTerminateError e) {
            // Do nothing -- we'll drop and re-connect anyway
         } catch (InterruptedException e) {
            // Ignore
         }
      }
   }

   /**
    * Report an error and terminate.
    */
   private void error(SJContext sjc, String msg) {
      sjc.stderr.println(msg);
      do_exit(1);
   }

   /**
    * Report an error with throwable, and terminate.
    */
   private void error(SJContext sjc, String msg, Throwable thr) {
      sjc.stderr.println(msg);
      thr.printStackTrace(sjc.stderr);
      do_exit(1);
   }

   /**
    * Look for incoming messages and process them if present.  If
    * 'block' is set, then will block until at least one message has
    * been received.
    */
   public void 
   poll_incoming(boolean block) {
     next_message:
      while (true) {
         if (!block) {
            try {
               if (0 == in.available())
                  return;
            } catch (IOException e) {
               do_exit(199);
            }
         }
         
         // If blocking set, it only applies to first message
         block = false;
         
         synchronized (reader) {
            try {
               reader.read();
            } catch (EOFException e) {
               // Maybe front end terminated via kill, try to clean up
               throw new SJTerminateError();
            } catch (IOException e) {
               log(id + ": IOException on input pipe: " + e.getMessage());
               throw new SJTerminateError();
            }
            
            Object[] oa;
            if (null != (oa = reader.match("0%t"))) {
               stdin.poll_add_data((byte[]) oa[0]);
               continue;
            }
            if (null != (oa = reader.match("EOF"))) {
               stdin.poll_set_eof();
               continue;
            }
            if (null != (oa = reader.match("run-status %i %i"))) {
               run_result = new RunResult((Integer) oa[0], (Integer) oa[1]);
               continue;
            }
           next_mod:
            for (SJModule sjm : modules) {
               byte[] pre = sjm.getPrefix();
               if (pre.length > reader.msg.length) continue;
               for (int a = 0; a<pre.length; a++)
                  if (pre[a] != reader.msg[a])
                     continue next_mod;
               if (sjm.match(reader))
                  continue next_message;
            }
            log(id + ": Invalid message received: " +
                new String(reader.msg, Scramjet.charset));
         }
      }
   }

   /**
    * Change the task to an 'exiting' state, and throw the
    * SJTerminateError.
    */
   public void do_exit(Tool tool, int status) throws SJTerminateError {
      if (tool == curr_tool) {
         terminated = true;
         exit_status = status;
      }
      throw new SJTerminateError();
   }

   /**
    * Change the task to an 'exiting' state, and throw the
    * SJTerminateError.
    */
   public void do_exit(MsgWriter writer, int status) throws SJTerminateError {
      if (writer == this.writer) {
         terminated = true;
         exit_status = status;
      }
      throw new SJTerminateError();
   }

   /**
    * Cause the tool to exit.
    */
   private void do_exit(int status) throws SJTerminateError {
      terminated = true;
      exit_status = status;
      throw new SJTerminateError();
   }
   
   /**
    * Add this module to the list of modules to check when
    * interpreting incoming messages, and connect it to the current
    * SJProxy and MsgWriter instances.
    */
   public void useModule(SJModule mod) {
      modules.add(mod);
      mod.setup(this, writer);
   }

   /**
    * Run an external tool for user-interaction.  Runs it using the
    * system() call, which accepts a command to be passed to the shell
    * to execute.  This also blocks SIGINT and SIGQUIT, so that user
    * can interrupt the called program without upsetting this one.
    *
    * <p>If this app is using {@link net.uazu.scramjet.mod.ConsoleMod}
    * and is in raw input mode, then it should revert to normal mode
    * first before calling this method.  If using {@link
    * net.uazu.con.Console}, then the {@link
    * net.uazu.con.Console#pause} method takes care of this.
    */
   public RunResult system(String cmd) {
      writer.write("run %s", cmd);
      writer.flush();
      
      // Wait for result to come back
      run_result = null;
      while (run_result == null)
         poll_incoming(true);

      return run_result;
   }
}
      
