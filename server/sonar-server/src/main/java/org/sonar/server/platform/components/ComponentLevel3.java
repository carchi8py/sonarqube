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

import org.sonar.api.utils.UriReader;
import org.sonar.core.util.DefaultHttpDownloader;
import org.sonar.jpa.session.DatabaseSessionProvider;
import org.sonar.jpa.session.DefaultDatabaseConnector;
import org.sonar.jpa.session.ThreadLocalDatabaseSessionFactory;
import org.sonar.server.platform.PersistentSettings;
import org.sonar.server.platform.ServerIdGenerator;
import org.sonar.server.startup.ServerMetadataPersister;

public class ComponentLevel3 extends ComponentLevel {
  public ComponentLevel3(ComponentLevel parent) {
    super("level3", parent);
  }

  @Override
  public ComponentLevel configure() {
    add(
      PersistentSettings.class,
      DefaultDatabaseConnector.class,
      ThreadLocalDatabaseSessionFactory.class,
      new DatabaseSessionProvider(),
      ServerMetadataPersister.class,
      DefaultHttpDownloader.class,
      UriReader.class,
      ServerIdGenerator.class);
    return this;
  }
}
