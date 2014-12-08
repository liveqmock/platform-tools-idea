/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerBorder implements Border, UIResource {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final JSpinner spinner = (JSpinner)c;
    final JFormattedTextField editor = UIUtil.findComponentOfType(spinner, JFormattedTextField.class);
    final int x1 = x + 3;
    final int y1 = y + 3;
    final int width1 = width - 8;
    final int height1 = height - 6;
    final boolean focused = c.isEnabled() && c.isVisible() && editor != null && editor.hasFocus();
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    if (c.isOpaque()) {
      g.setColor(UIUtil.getPanelBackground());
      g.fillRect(x, y, width, height);
    }

    g.setColor(UIUtil.getTextFieldBackground());
    g.fillRoundRect(x1, y1, width1, height1, 5, 5);
    g.setColor(UIUtil.getPanelBackground());
    if (editor != null) {
      final int off = editor.getBounds().x + editor.getWidth() + ((JSpinner)c).getInsets().left + 1;
      g.fillRect(off, y1, 17, height1);
      g.setColor(Gray._100);
      g.drawLine(off, y1, off, height1+2);
    }

    if (!c.isEnabled()) {
      ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
    }

    if (focused) {
      DarculaUIUtil.paintFocusRing(g, x1, y1, width1, height1);
    } else {
      g.setColor(Gray._100);
      g.drawRoundRect(x1, y1, width1, height1, 5, 5);
    }
    config.restore();
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(6, 7, 6, 7);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}