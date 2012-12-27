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

/**
 * Immutable: stores the context that a Tool will use to run.
 */
public class SJContext {
   /**
    * SJProxy.
    */
   public final SJProxy proxy;
   
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
    * Base constructor.
    */
   public SJContext(SJProxy proxy, String[] args, Map<String,String> env, File cwd, String cmd) {
      this.proxy = proxy;
      this.args = args;
      this.env = env;
      this.cwd = cwd;
      this.cmd = cmd;
      stdin = null;
      stdout = null;
      stderr = null;
   }

   /**
    * Constructor to add the streams.
    */
   public SJContext(SJContext orig, InputStream in, PrintStream out, PrintStream err) {
      proxy = orig.proxy;
      args = orig.args;
      env = orig.env;
      cwd = orig.cwd;
      cmd = orig.cmd;
      stdin = in;
      stdout = out;
      stderr = err;
   }
}
      
