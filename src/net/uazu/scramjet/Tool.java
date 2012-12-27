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
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

import net.uazu.scramjet.SJProxy.RunResult;

/**
 * Context for a command tool to run in: should be subclassed by
 * individual tool main classes.  Provides convenient access to
 * various pieces of data (mostly immutable), plus convenience methods
 * for various operations.  In addition a tool may use System.in,
 * System.out, System.err and System.exit() which work but are slower
 * and less direct than the access provided here.
 */
public abstract class Tool {
   /**
    * SJProxy.
    */
   private final SJProxy proxy;
   
   /**
    * Command-line arguments.
    */
   public final String[] args;

   /**
    * Command environment variables.
    */
   public final Map<String,String> env;

   /**
    * Current working directory of command.
    */
   public final File cwd;

   /**
    * Command-name used for invocation.
    */
   public final String cmd;

   /**
    * Standard input.  Slightly quicker and more direct than using
    * System.in.
    */
   public final InputStream stdin;
   
   /**
    * Standard output.  Slightly quicker and more direct than using
    * System.out.
    */
   public final PrintStream stdout;

   /**
    * Standard error.  Slightly quicker and more direct than using
    * System.err.
    */
   public final PrintStream stderr;
   
   /**
    * Constructor.
    */
   public Tool(SJContext sjc) {
      proxy = sjc.proxy;
      args = sjc.args;
      env = sjc.env;
      cwd = sjc.cwd;
      cmd = sjc.cmd;
      stdin = sjc.stdin;
      stdout = sjc.stdout;
      stderr = sjc.stderr;
   }

   /**
    * Method which should be overridden in subclasses to execute the
    * command's functionality: reading 'args'/etc, reporting errors
    * and/or performing actions.  If any exception is thrown, a trace
    * is dumped to stderr and the tool terminates.
    */
   public abstract void run() throws Exception;

   /**
    * Print a newline to stdout: shortcut for
    * <code>stdout.println()</code>
    */
   public final void println() {
      stdout.println();
   }

   /**
    * Print a string followed by a newline to stdout: shortcut for
    * <code>stdout.println(msg)</code>
    */
   public final void println(String msg) {
      stdout.println(msg);
   }

   /**
    * Print a string to stdout without a newline: shortcut for
    * <code>stdout.print(msg)</code>
    */
   public final void print(String msg) {
      stdout.print(msg);
   }

   /**
    * Print a formatted string to stdout: shortcut for
    * <code>stdout.format(fmt, args)</code>
    */
   public final void printf(String fmt, Object... args) {
      stdout.format(fmt, args);
   }

   /**
    * Flush stdout: shortcut for <code>stdout.flush()
    */
   public final void flush() {
      stdout.flush();
   }

   /**
    * Send a BEL character immediately to stdout
    */
   public final void beep() {
      writeOut(new byte[] { 7 }, 1);
   }

   /**
    * Linux console beep for 'dur' ms at pitch 'pit'.  The call blocks
    * until the notes have finished playing.  On terminals which don't
    * understand the control sequences it will come out as a sequence
    * of normal untuned beeps.
    *
    * <p>Pitch is expressed in 100ths of semitone above concert-A
    * (440Hz), or 9999 for a rest.  So a pitch of 0 is concert A
    * (440Hz), 1200 is the A an octave above it (880Hz), -1200 is the
    * A an octave below it (220Hz).  A fast rising A major arpeggio
    * could be played with beep(50, 0, 50, 400, 50, 700, 50, 1200).  A
    * slow rising C major arpeggio starting on middle C could be
    * played with beep(200, -900, 200, -500, 200, -200, 200, 300).
    * 
    * @param dur Duration in ms
    * @param pit Pitch in 100ths of a semitone above concert A, or
    * 9999 for a rest (silent period)
    * @param cont Continuation, pairs of (dur,pit) ints for additional
    * notes or rests to play
    */
   public final void beep(int dur, int pit, int... cont) {
      final double PITMUL = 1/1200.0;
      long now;
      long stop;
      int ci = 0;
      writeOutASCII("\033[11;1000]");   // Length 1 second
      while (true) {
         int freq = (int) Math.floor(0.5 + 440.0 * Math.pow(2, pit * PITMUL));
         if (freq > 20000)
            freq = 0;

         // Set pitch and beep
         writeOutASCII(String.format("\033[10;%d]\007", freq));

         // Wait duration
         now = System.currentTimeMillis();
         stop = now + dur;
         while (now < stop) {
            try {
               Thread.sleep(stop-now);
            } catch (InterruptedException e) {}
            now = System.currentTimeMillis();
         }
         if (ci+1 >= cont.length)
            break;
         dur = cont[ci++];
         pit = cont[ci++];
      }
      writeOutASCII("\033[11;10]\033[10;0]\007" + // To stop sound
                    "\033[11;100]\033[10;750]");  // To restore defaults
   }

   // Not for public use; only suitable for ASCII
   private final void writeOutASCII(String str) {
      int len = str.length();
      byte[] tmp = new byte[len];
      for (int a = 0; a<len; a++)
         tmp[a] = (byte) str.charAt(a);
      writeOut(tmp, len);
   }

   /**
    * Low-level write byte[] data directly to stdout.  Bypasses
    * character-set conversion and buffering of 'stdout' stream.
    */
   public final void writeOut(byte[] data, int count) {
      if (proxy.curr_tool == this) {
         proxy.writer.write("1%t", data, count);
         proxy.writer.flush();
      }
   }

   /**
    * Low-level write byte[] data directly to stderr.  Bypasses
    * character-set conversion and buffering of 'stderr' stream.
    */
   public final void writeErr(byte[] data, int count) {
      if (proxy.curr_tool == this) {
         proxy.writer.write("2%t", data, count);
         proxy.writer.flush();
      }
   }

   /**
    * Report a formatted error to STDERR (with added \n) and terminate
    * tool with status 1.
    */
   public final void error(String fmt, Object... args) {
      stderr.println(String.format(fmt, args));
      exit(1);
   }

   /**
    * Write a logging message to the scramjet log, which appears in
    * the .scramjet/ folder.  This is useful within a curses-style app
    * where output to stderr may be hidden.
    */
   public final void log(String msg) {
      Scramjet.log(msg);
   }

   /**
    * Write a formatted logging message to the scramjet log, which
    * appears in the .scramjet/ folder.  This is useful within a
    * curses-style app where output to stderr may be hidden.
    */
   public final void logf(String fmt, Object... args) {
      Scramjet.log(String.format(fmt, args));
   }

   /**
    * Terminate the tool with the given exit status.  Slightly quicker
    * and more direct than doing System.exit(status).
    */
   public final void exit(int status) throws SJTerminateError {
      proxy.do_exit(this, status);
   }

   /**
    * Test to see whether this Tool has terminated, and throw
    * SJTerminateError if it has.  If this Tool starts extra threads,
    * they should check from time to time by calling this method to
    * see if the parent Tool has finished.  The Tool could die at any
    * point if the front-end is terminated, leaving its threads
    * running, and there is no mechanism available in Java to clean
    * them up.  So they must check themselves and terminate
    * gracefully.  You can check for runaway threads with the {@link
    * tool.SJThreads} tool.  The SJTerminateError thrown is an Error
    * and is intended to bypass all catch clauses and terminate the
    * thread.
    */
   public final void exitCheck() throws SJTerminateError {
      if (proxy.curr_tool != this || proxy.terminated)
         throw new SJTerminateError();
   }

//   /**
//    * Make the whole Java VM (i.e. the Scramjet server) shut down as
//    * soon as this tool has exited.
//    */
//   public final void shutdown() {
//      proxy.shutdown = true;
//   }

   /**
    * Run an external command for user interaction using the system()
    * call.  Waits for the given command to complete.  If using {@link
    * net.uazu.scramjet.mod.ConsoleMod}, then raw input mode should be
    * disabled first.  If using {@link net.uazu.con.Console} then
    * {@link net.uazu.con.Console#pause} takes care of this.
    */
   public RunResult system(String cmd) {
      return proxy.system(cmd);
   }
   
   /**
    * Add a module to this session.  A module communicates with the C
    * front-end to give access to additional facilities.  See for
    * example {@link mod.ConsoleMod} for access to raw-mode input and
    * terminal window size.
    */
   public final void useModule(SJModule mod) {
      proxy.useModule(mod);
   }
}
      
