/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
* User: cdr
*/
class DirElementInfo extends FileElementInfo {
  public DirElementInfo(@NotNull PsiDirectory directory) {
    super(directory.getProject(), directory.getVirtualFile());
  }

  @Override
  public PsiElement restoreElement() {
    return SelfElementInfo.restoreDirectoryFromVirtual(myVirtualFile, myProject);
  }
}
