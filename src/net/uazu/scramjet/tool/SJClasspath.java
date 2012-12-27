/*   

  Copyright 2004, Martian Software, Inc.
  Copyright 2011, Jim Peters <http://uazu.net>

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package net.uazu.scramjet.tool;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Scramjet;
import net.uazu.scramjet.Tool;


/**
 * <p>Provides a means to display and add to the system classpath at
 * runtime.  If called with no arguments, the classpath is displayed.
 * Otherwise, each argument is turned into a java.io.File and added to
 * the classpath.  Relative paths will be resolved relative to the
 * caller's working directory.</p>
 * 
 * <p>This is aliased by default to the command
 * "<code>sj-cp</code>".</p>
 * 
 * @author <a href="http://www.martiansoftware.com/contact.html">Marty Lamb</a>
 */
public class SJClasspath extends Tool {
   public SJClasspath(SJContext sjc) {
      super(sjc);
   }
   public void run() throws Exception {
      if (args.length == 0) {
         URLClassLoader sysLoader =
            (URLClassLoader) ClassLoader.getSystemClassLoader();
         for (URL url : sysLoader.getURLs())
            println(url.toString());
      } else {
         for (String arg : args) {
            File file = new File(arg);
            if (!file.isAbsolute())
               file = new File(cwd, arg);
            // Check 'exists' here to display error instead of logging
            if (!file.exists())
               stderr.println("Directory or JAR file does not exist:\n  " + file);
            else 
               Scramjet.addClassPath(file);
         }
      }
   }
}
