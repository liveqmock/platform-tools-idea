/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

import java.util.List;

/**
 * @author nik
 */
public class XEditWatchAction extends XWatchesTreeActionBase {
  @Override
  public void update(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    e.getPresentation().setVisible(tree != null && getSelectedNodes(tree, WatchNode.class).size() == 1);
    super.update(e);
  }

  @Override
  protected void perform(AnActionEvent e, XDebuggerTree tree) {
    List<? extends WatchNode> watchNodes = getSelectedNodes(tree, WatchNode.class);
    if (watchNodes.size() != 1) return;

    WatchNode node = watchNodes.get(0);
    XDebuggerTreeNode root = tree.getRoot();
    if (root instanceof WatchesRootNode) {
      ((WatchesRootNode)root).editWatch(node);
    }
  }
}