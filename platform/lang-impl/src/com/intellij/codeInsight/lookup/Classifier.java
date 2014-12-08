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
package com.intellij.codeInsight.lookup;

import com.intellij.util.ProcessingContext;

import java.util.LinkedHashMap;

/**
 * @author peter
 */
public abstract class Classifier<T> {
  public abstract void addElement(T t);

  public abstract Iterable<T> classify(Iterable<T> source, ProcessingContext context);

  public abstract void describeItems(LinkedHashMap<T, StringBuilder> map, ProcessingContext context);

}
