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
package com.intellij.util.ui;

import com.intellij.util.ListWithSelection;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

/**
 * author: lesya
 */
public class ComboBoxTableCellEditor extends AbstractTableCellEditor {
  public final static ComboBoxTableCellEditor INSTANCE = new ComboBoxTableCellEditor();

  private final JPanel myPanel = new JPanel(new GridBagLayout());
  private final JComboBox myComboBox = new JComboBox();

  private ComboBoxTableCellEditor() {
    myComboBox.setRenderer(new BasicComboBoxRenderer());
    myComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        stopCellEditing();
      }
    });
    myPanel.add(myComboBox,
                new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0,
                                       0));
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final ListWithSelection options = (ListWithSelection)value;
    if (options.getSelection() == null) {
      options.selectFirst();
    }
    myComboBox.removeAllItems();
    for (Iterator each = options.iterator(); each.hasNext();) {
      myComboBox.addItem(each.next());
    }

    myComboBox.setSelectedItem(options.getSelection());

    return myPanel;
  }

  public Object getCellEditorValue() {
    return myComboBox.getSelectedItem();
  }

  public Dimension getPreferedSize() {
    return myComboBox.getPreferredSize();
  }

}
