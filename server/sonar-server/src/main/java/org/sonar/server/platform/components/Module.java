/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.platform.components;

import java.util.Collection;
import javax.annotation.Nullable;
import org.sonar.core.platform.ComponentContainer;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class Module {
  private ComponentContainer container;

//  public void start() {
//    // forces instantiation and start of implicit dependencies
//    for (Class<?> aClass : implicitDependencies()) {
//      container.getPicoContainer().getComponent(aClass);
//    }
//  }
//
//  public Collection<Class<?>> implicitDependencies() {
//    return Collections.emptyList();
//  }

  public Module configure(ComponentContainer container) {
    this.container = checkNotNull(container);

    configureModule();

    return this;
  }

  protected abstract void configureModule();

  protected void add(@Nullable Object object, boolean singleton) {
    if (object != null) {
      container.addComponent(object, singleton);
    }
  }

  protected <T> T getComponentByType(Class<T> tClass) {
    return container.getComponentByType(tClass);
  }

  protected void add(Object... objects) {
    for (Object object : objects) {
      if (object != null) {
        container.addComponent(object, true);
      }
    }
  }

  protected void addAll(Collection<?> objects) {
    add(objects.toArray(new Object[objects.size()]));
  }

}
