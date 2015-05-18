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

package org.sonar.server.computation.step;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.sonar.batch.protocol.Constants;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.batch.protocol.output.BatchReportReader;
import org.sonar.batch.protocol.output.BatchReportWriter;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.computation.ComputationContext;
import org.sonar.server.db.DbClient;
import org.sonar.test.DbTests;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@Category(DbTests.class)
public class PersistComponentsStepTest extends BaseStepTest {

  @ClassRule
  public static DbTester dbTester = new DbTester();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  File reportDir;

  DbSession session;

  DbClient dbClient;

  PersistComponentsStep sut;

  @Before
  public void setup() throws Exception {
    dbTester.truncateTables();
    session = dbTester.myBatis().openSession(false);
    dbClient = new DbClient(dbTester.database(), dbTester.myBatis(), new ComponentDao());

    reportDir = temp.newFolder();

    sut = new PersistComponentsStep(dbClient);
  }

  @Override
  protected ComputationStep step() {
    return sut;
  }

  @After
  public void tearDown() {
    session.close();
  }

  @Test
  public void persist_components() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .setName("Module")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    ComponentDto project = dbClient.componentDao().selectNullableByKey(session, "PROJECT_KEY");
    assertThat(project).isNotNull();
    assertThat(project.name()).isEqualTo("Project");
    assertThat(project.uuid()).isNotNull();
    assertThat(project.moduleUuid()).isNull();
    assertThat(project.moduleUuidPath()).isEqualTo("." + project.uuid() + ".");
    assertThat(project.projectUuid()).isEqualTo(project.uuid());
    assertThat(project.qualifier()).isEqualTo("TRK");
    assertThat(project.scope()).isEqualTo("PRJ");
    assertThat(project.parentProjectId()).isNull();

    ComponentDto module = dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY");
    assertThat(module).isNotNull();
    assertThat(module.name()).isEqualTo("Module");
    assertThat(module.uuid()).isNotNull();
    assertThat(module.moduleUuid()).isEqualTo(project.uuid());
    assertThat(module.moduleUuidPath()).isEqualTo("." + project.uuid() + "." + module.uuid() + ".");
    assertThat(module.projectUuid()).isEqualTo(project.uuid());
    assertThat(module.qualifier()).isEqualTo("BRC");
    assertThat(module.scope()).isEqualTo("PRJ");
    assertThat(module.parentProjectId()).isEqualTo(project.getId());

    ComponentDto directory = dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:src/main/java/dir");
    assertThat(directory).isNotNull();
    assertThat(directory.name()).isEqualTo("src/main/java/dir");
    assertThat(directory.path()).isEqualTo("src/main/java/dir");
    assertThat(directory.uuid()).isNotNull();
    assertThat(directory.moduleUuid()).isEqualTo(module.uuid());
    assertThat(directory.moduleUuidPath()).isEqualTo(module.moduleUuidPath());
    assertThat(directory.projectUuid()).isEqualTo(project.uuid());
    assertThat(directory.qualifier()).isEqualTo("DIR");
    assertThat(directory.scope()).isEqualTo("DIR");
    assertThat(directory.parentProjectId()).isEqualTo(module.getId());

    ComponentDto file = dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:src/main/java/dir/Foo.java");
    assertThat(file).isNotNull();
    assertThat(file.name()).isEqualTo("Foo.java");
    assertThat(file.path()).isEqualTo("src/main/java/dir/Foo.java");
    assertThat(file.uuid()).isNotNull();
    assertThat(file.moduleUuid()).isEqualTo(module.uuid());
    assertThat(file.moduleUuidPath()).isEqualTo(module.moduleUuidPath());
    assertThat(file.projectUuid()).isEqualTo(project.uuid());
    assertThat(file.qualifier()).isEqualTo("FIL");
    assertThat(file.scope()).isEqualTo("FIL");
    assertThat(file.parentProjectId()).isEqualTo(module.getId());
  }

  @Test
  public void persist_file_on_root() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("/")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.FILE)
      .setPath("pom.xml")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    ComponentDto directory = dbClient.componentDao().selectNullableByKey(session, "PROJECT_KEY:/");
    assertThat(directory).isNotNull();
    assertThat(directory.name()).isEqualTo("/");
    assertThat(directory.path()).isEqualTo("/");

    ComponentDto file = dbClient.componentDao().selectNullableByKey(session, "PROJECT_KEY:pom.xml");
    assertThat(file).isNotNull();
    assertThat(file.name()).isEqualTo("pom.xml");
    assertThat(file.path()).isEqualTo("pom.xml");
  }

  @Test
  public void persist_unit_test() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/test/java/dir")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/test/java/dir/FooTest.java")
      .setIsTest(true)
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    ComponentDto file = dbClient.componentDao().selectNullableByKey(session, "PROJECT_KEY:src/test/java/dir/FooTest.java");
    assertThat(file).isNotNull();
    assertThat(file.name()).isEqualTo("FooTest.java");
    assertThat(file.path()).isEqualTo("src/test/java/dir/FooTest.java");
    assertThat(file.qualifier()).isEqualTo("UTS");
    assertThat(file.scope()).isEqualTo("FIL");
  }


  @Test
  public void use_latest_module_for_files_key() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .setName("Module")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.MODULE)
      .setKey("SUB_MODULE_KEY")
      .setName("Sub Module")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(5)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(5)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    assertThat(dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY")).isNotNull();
    assertThat(dbClient.componentDao().selectNullableByKey(session, "SUB_MODULE_KEY")).isNotNull();
    assertThat(dbClient.componentDao().selectNullableByKey(session, "SUB_MODULE_KEY:src/main/java/dir")).isNotNull();
    assertThat(dbClient.componentDao().selectNullableByKey(session, "SUB_MODULE_KEY:src/main/java/dir/Foo.java")).isNotNull();
  }

  @Test
  public void persist_with_branch() throws Exception {
    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .setBranch("origin/master")
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .setName("Module")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    assertThat(dbClient.componentDao().selectNullableByKey(session, "PROJECT_KEY:origin/master")).isNotNull();
    assertThat(dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:origin/master")).isNotNull();
    assertThat(dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:origin/master:src/main/java/dir")).isNotNull();
    assertThat(dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:origin/master:src/main/java/dir/Foo.java")).isNotNull();
  }

  @Test
  public void persist_only_new_components() throws Exception {
    // TODO some components already exists in db




    File reportDir = temp.newFolder();
    BatchReportWriter writer = new BatchReportWriter(reportDir);
    writer.writeMetadata(BatchReport.Metadata.newBuilder()
      .setRootComponentRef(1)
      .build());

    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .setKey("PROJECT_KEY")
      .setName("Project")
      .addChildRef(2)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .setKey("MODULE_KEY")
      .setName("Module")
      .addChildRef(3)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(3)
      .setType(Constants.ComponentType.DIRECTORY)
      .setPath("src/main/java/dir")
      .addChildRef(4)
      .build());
    writer.writeComponent(BatchReport.Component.newBuilder()
      .setRef(4)
      .setType(Constants.ComponentType.FILE)
      .setPath("src/main/java/dir/Foo.java")
      .build());

    sut.execute(new ComputationContext(new BatchReportReader(reportDir), ComponentTesting.newProjectDto()));

    ComponentDto project = dbClient.componentDao().selectNullableByKey(session, "PROJECT_KEY");
    assertThat(project).isNotNull();
    assertThat(project.name()).isEqualTo("Project");
    assertThat(project.uuid()).isNotNull();
    assertThat(project.moduleUuid()).isNull();
    assertThat(project.moduleUuidPath()).isEqualTo("." + project.uuid() + ".");
    assertThat(project.projectUuid()).isEqualTo(project.uuid());
    assertThat(project.qualifier()).isEqualTo("TRK");
    assertThat(project.scope()).isEqualTo("PRJ");
    assertThat(project.parentProjectId()).isNull();

    ComponentDto module = dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY");
    assertThat(module).isNotNull();
    assertThat(module.name()).isEqualTo("Module");
    assertThat(module.uuid()).isNotNull();
    assertThat(module.moduleUuid()).isEqualTo(project.uuid());
    assertThat(module.moduleUuidPath()).isEqualTo("." + project.uuid() + "." + module.uuid() + ".");
    assertThat(module.projectUuid()).isEqualTo(project.uuid());
    assertThat(module.qualifier()).isEqualTo("BRC");
    assertThat(module.scope()).isEqualTo("PRJ");
    assertThat(module.parentProjectId()).isEqualTo(project.getId());

    ComponentDto directory = dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:src/main/java/dir");
    assertThat(directory).isNotNull();
    assertThat(directory.name()).isEqualTo("src/main/java/dir");
    assertThat(directory.path()).isEqualTo("src/main/java/dir");
    assertThat(directory.uuid()).isNotNull();
    assertThat(directory.moduleUuid()).isEqualTo(module.uuid());
    assertThat(directory.moduleUuidPath()).isEqualTo(module.moduleUuidPath());
    assertThat(directory.projectUuid()).isEqualTo(project.uuid());
    assertThat(directory.qualifier()).isEqualTo("DIR");
    assertThat(directory.scope()).isEqualTo("DIR");
    assertThat(directory.parentProjectId()).isEqualTo(module.getId());

    ComponentDto file = dbClient.componentDao().selectNullableByKey(session, "MODULE_KEY:src/main/java/dir/Foo.java");
    assertThat(file).isNotNull();
    assertThat(file.name()).isEqualTo("Foo.java");
    assertThat(file.path()).isEqualTo("src/main/java/dir/Foo.java");
    assertThat(file.uuid()).isNotNull();
    assertThat(file.moduleUuid()).isEqualTo(module.uuid());
    assertThat(file.moduleUuidPath()).isEqualTo(module.moduleUuidPath());
    assertThat(file.projectUuid()).isEqualTo(project.uuid());
    assertThat(file.qualifier()).isEqualTo("FIL");
    assertThat(file.scope()).isEqualTo("FIL");
    assertThat(file.parentProjectId()).isEqualTo(module.getId());
  }

  @Test
  public void nothing_to_persist() throws Exception {

  }


}
