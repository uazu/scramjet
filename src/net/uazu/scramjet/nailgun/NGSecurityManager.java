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

package net.uazu.scramjet.nailgun;

import java.security.Permission;

import net.uazu.scramjet.Tool;


/**
 * Security manager which does nothing other than trap
 * checkExit, or delegate all non-deprecated methods to
 * a base manager.
 * 
 * @author Pete Kirkham
 */
public class NGSecurityManager extends SecurityManager {
   private static final ThreadLocal<Tool> TOOL = new InheritableThreadLocal<Tool>();
   private final SecurityManager base;
   
   /**
    * Construct an NGSecurityManager with the given base.
    * @param base the base security manager, or null for no base.
    */
   public NGSecurityManager(SecurityManager base) {
      this.base = base;
   }
   
   public static void setTool(Tool tool) {
      TOOL.set(tool);
   }
   
   public void checkExit(int status) {
      if (base != null) {
         base.checkExit(status);
      }
      
      final Tool tool = TOOL.get();
      
      if (tool != null) {
         tool.exit(status);
      }
   }
   
   public void checkPermission(Permission perm) {
      if (base != null) {
         base.checkPermission(perm);
      }
   }
   
   public void checkPermission(Permission perm, Object context) {
      if (base != null) {
         base.checkPermission(perm, context);
      }
   }
}