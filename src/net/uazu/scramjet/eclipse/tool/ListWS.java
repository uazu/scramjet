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

package net.uazu.scramjet.eclipse.tool;

import net.uazu.scramjet.SJContext;
import net.uazu.scramjet.Tool;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;


/**
 * List workspace components.
 */
public class ListWS extends Tool {
   public ListWS(SJContext sjc) { super(sjc); }

   public void run() throws Exception {
      final IWorkspace workspace = ResourcesPlugin.getWorkspace();
      dump(workspace.getRoot(), 0);
   }

   public void indent(int lev) {
      while (lev-- > 0)
         print(" ");
   }
   
   public void dump(IContainer parent, int lev) throws CoreException {
      for (IResource res : parent.members()) {
         if (res instanceof IProject) {
            IProject pr = (IProject) res;
            indent(lev);
            println("Project: " + pr.getName());
            pr.open(null);
            dump(pr, lev+2);
            continue;
         }
         if (res instanceof IFolder) {
            IFolder fold = (IFolder) res;
            indent(lev);
            println("Folder: " + fold.getName());
            dump(fold, lev+2);
            continue;
         }
         if (res instanceof IFile) {
            IFile file = (IFile) res;
            indent(lev);
            println("File: " + file.getName());
            continue;
         }
         indent(lev);
         println("Unknown: " + res.getClass().getSimpleName() + ": " + res.getName());
      }
   }
}