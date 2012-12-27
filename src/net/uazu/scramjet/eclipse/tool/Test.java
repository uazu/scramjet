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

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.JavaCore;


/**
 * List Java model components.
 */
public class Test extends Tool {
   public Test(SJContext sjc) { super(sjc); }

   public void run() throws Exception {
      final IWorkspace workspace = ResourcesPlugin.getWorkspace();
      final IJavaModel model = JavaCore.create(workspace.getRoot());
      dump(model, 0);
   }

   public void indent(int lev) {
      while (lev-- > 0)
         print("  ");
   }
   
   public void dump(IJavaElement item, int lev) throws Exception {
      indent(lev);
      println(item.getClass().getSimpleName() + ": " + item.getElementName() +
              " (" + item.getElementType() + ")");
      if (item instanceof IParent) {
         for (IJavaElement ie : ((IParent) item).getChildren())
            dump(ie, lev+1);
      }
   }
}