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
package org.sonar.server.db.migrations.v51;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.db.migrations.MigrationStep;

import java.sql.Types;

public class AddIssuesColumnsTest {

  @ClassRule
  public static DbTester db = new DbTester().schema(AddIssuesColumnsTest.class, "schema.sql");

  MigrationStep migration;

  @Before
  public void setUp() {
    migration = new AddIssuesColumns(db.database());
  }

  @Test
  public void update_columns() throws Exception {
    migration.execute();

    db.assertColumnDefinition("issues", "issue_creation_date_ms", Types.BIGINT, null);
    db.assertColumnDefinition("issues", "issue_update_date_ms", Types.BIGINT, null);
    db.assertColumnDefinition("issues", "issue_close_date_ms", Types.BIGINT, null);
    db.assertColumnDefinition("issues", "tags", Types.VARCHAR, 4000);
    db.assertColumnDefinition("issues", "component_uuid", Types.VARCHAR, 50);
    db.assertColumnDefinition("issues", "project_uuid", Types.VARCHAR, 50);
  }

}
