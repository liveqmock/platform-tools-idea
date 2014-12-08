/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;

/**
 * @author: db
 * Date: 01.02.11
 */
class FieldRepr extends ProtoMember {
  public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
    myType.updateClassUsages(context, owner, s);
  }

  public FieldRepr(final DependencyContext context, final int a, final int n, final int d, final int s, final Object v) {
    super(a, s, n, TypeRepr.getType(context, d), v);
  }

  public FieldRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FieldRepr fieldRepr = (FieldRepr)o;

    return name == fieldRepr.name;
  }

  @Override
  public int hashCode() {
    return 31 * name;
  }

  public static DataExternalizer<FieldRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<FieldRepr>() {
      @Override
      public void save(final DataOutput out, final FieldRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public FieldRepr read(final DataInput in) throws IOException {
        return new FieldRepr(context, in);
      }
    };
  }

  public UsageRepr.Usage createUsage(final DependencyContext context, final int owner) {
    return UsageRepr.createFieldUsage(context, name, owner, context.get(myType.getDescr(context)));
  }

  public UsageRepr.Usage createAssignUsage(final DependencyContext context, final int owner) {
    return UsageRepr.createFieldAssignUsage(context, name, owner, context.get(myType.getDescr(context)));
  }
}