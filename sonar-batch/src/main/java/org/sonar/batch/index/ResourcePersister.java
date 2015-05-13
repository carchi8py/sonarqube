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
package org.sonar.batch.index;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.database.DatabaseSession;
import org.sonar.api.database.model.ResourceModel;
import org.sonar.api.database.model.Snapshot;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.api.resources.Scopes;
import org.sonar.api.security.ResourcePermissions;
import org.sonar.api.utils.SonarException;
import org.sonar.api.utils.internal.Uuids;
import org.sonar.core.component.ScanGraph;

import javax.annotation.Nullable;
import javax.persistence.NonUniqueResultException;

import static org.sonar.api.utils.DateUtils.dateToLong;

public class ResourcePersister implements ScanPersister {

  @VisibleForTesting
  static final String MODULE_UUID_PATH_SEPARATOR = ".";

  private final DatabaseSession session;
  private final ResourcePermissions permissions;
  private final ResourceCache resourceCache;
  private final ScanGraph scanGraph;

  public ResourcePersister(DatabaseSession session, ResourcePermissions permissions, ResourceCache resourceCache, ScanGraph scanGraph) {
    this.session = session;
    this.permissions = permissions;
    this.resourceCache = resourceCache;
    this.scanGraph = scanGraph;
  }

  @Override
  public void persist() {
    for (BatchResource resource : resourceCache.all()) {
      persist(resource);
    }
  }

  private void persist(BatchResource batchResource) {
    if (batchResource.snapshot() != null) {
      // already persisted
      return;
    }
    BatchResource parentBatchResource = batchResource.parent();
    Snapshot s;
    if (parentBatchResource != null) {
      persist(parentBatchResource);
      s = persist(findModule(parentBatchResource), batchResource.resource(), parentBatchResource.resource());
    } else {
      // Root project
      s = persistProject((Project) batchResource.resource(), null);
    }
    batchResource.setSnapshot(s);
    if (ResourceUtils.isPersistable(batchResource.resource())) {
      scanGraph.completeComponent(batchResource.key(), batchResource.resource().getId(), s.getId());
    }
  }

  private Project findModule(BatchResource batchResource) {
    if (batchResource.resource() instanceof Project) {
      return (Project) batchResource.resource();
    } else {
      return findModule(batchResource.parent());
    }
  }

  private Snapshot persistProject(Project project, @Nullable Project parent) {
    ResourceModel model = findOrCreateModel(project, parent);
    // Used by ResourceKeyMigration in order to know that a project has already being migrated
    model.setDeprecatedKey(project.getKey());
    // language is null for project since multi-language support
    model.setLanguageKey(null);

    // For views
    if (project instanceof ResourceCopy) {
      model.setCopyResourceId(((ResourceCopy) project).getCopyResourceId());
    }

    Snapshot parentSnapshot = null;
    if (parent != null) {
      // assume that the parent project has already been saved
      parentSnapshot = resourceCache.get(parent.getEffectiveKey()).snapshot();
      model.setRootId((Integer) ObjectUtils.defaultIfNull(parentSnapshot.getRootProjectId(), parentSnapshot.getResourceId()));
    } else {
      model.setRootId(null);
    }
    model = session.save(model);
    project.setId(model.getId());
    project.setUuid(model.getUuid());

    Snapshot snapshot = new Snapshot(model, parentSnapshot);
    snapshot.setVersion(project.getAnalysisVersion());
    snapshot.setCreatedAtMs(dateToLong(project.getAnalysisDate()));
    snapshot.setBuildDateMs(System.currentTimeMillis());
    snapshot = session.save(snapshot);
    session.commit();

    if (parent == null && !permissions.hasRoles(project)) {
      permissions.grantDefaultRoles(project);
    }

    return snapshot;
  }

  Snapshot persist(Project project, Resource resource, @Nullable Resource parent) {
    Snapshot snapshot;
    if (resource instanceof Project) {
      // should not occur, please use the method saveProject()
      snapshot = persistProject((Project) resource, (Project) parent);
    } else {
      snapshot = persistFileOrDirectory(project, resource, parent);
    }

    return snapshot;
  }

  /**
   * Everything except project and library
   */
  private Snapshot persistFileOrDirectory(Project project, Resource resource, @Nullable Resource parentReference) {
    BatchResource moduleResource = resourceCache.get(project);
    Integer moduleId = moduleResource.resource().getId();
    ResourceModel model = findOrCreateModel(resource, parentReference != null ? parentReference : project);
    model.setRootId(moduleId);
    model = session.save(model);
    resource.setId(model.getId());
    resource.setUuid(model.getUuid());

    Snapshot parentSnapshot;
    if (parentReference != null) {
      parentSnapshot = resourceCache.get(parentReference).snapshot();
    } else {
      parentSnapshot = moduleResource.snapshot();
    }

    Snapshot snapshot = new Snapshot(model, parentSnapshot);
    snapshot.setBuildDateMs(System.currentTimeMillis());
    snapshot = session.save(snapshot);
    session.commit();
    return snapshot;
  }

  private ResourceModel findOrCreateModel(Resource resource, @Nullable Resource parentResource) {
    ResourceModel model;
    try {
      model = session.getSingleResult(ResourceModel.class, "key", resource.getEffectiveKey());
      if (model == null) {
        if (StringUtils.isBlank(resource.getEffectiveKey())) {
          throw new SonarException("Unable to persist resource " + resource.toString() + ". Resource effective key is blank. This may be caused by an outdated plugin.");
        }
        model = createModel(resource, parentResource);

      } else {
        mergeModel(model, resource);
      }
      updateUuids(resource, parentResource, model);
      return model;

    } catch (NonUniqueResultException e) {
      throw new SonarException("The resource '" + resource.getEffectiveKey() + "' is duplicated in database.", e);
    }
  }

  ResourceModel createModel(Resource resource, @Nullable Resource parentResource) {
    ResourceModel model = new ResourceModel();
    model.setEnabled(Boolean.TRUE);
    model.setDescription(resource.getDescription());
    model.setKey(resource.getEffectiveKey());
    model.setUuid(Uuids.create());
    model.setPath(resource.getPath());
    Language language = resource.getLanguage();
    if (language != null) {
      model.setLanguageKey(language.getKey());
    }
    if (StringUtils.isNotBlank(resource.getName())) {
      model.setName(resource.getName());
    } else {
      model.setName(resource.getKey());
    }
    model.setLongName(resource.getLongName());
    model.setScope(resource.getScope());
    model.setQualifier(resource.getQualifier());
    return model;
  }

  private void updateUuids(Resource resource, Resource parentResource, ResourceModel model) {
    // Don't override uuids when persisting a library and a project already exists
    if (ResourceUtils.isLibrary(resource) && !Qualifiers.LIBRARY.equals(model.getQualifier())) {
      return;
    }
    if (parentResource == null) {
      // Root module && libraries
      model.setProjectUuid(model.getUuid());
      model.setModuleUuidPath(MODULE_UUID_PATH_SEPARATOR + model.getUuid() + MODULE_UUID_PATH_SEPARATOR);
    } else {
      ResourceModel parentModel = session.getSingleResult(ResourceModel.class, "id", parentResource.getId());
      model.setProjectUuid(parentModel.getProjectUuid());
      if (Scopes.isProject(resource)) {
        // Sub module
        model.setModuleUuid(parentResource.getUuid());
        String parentModuleUuidPath = parentModel.getModuleUuidPath();
        model.setModuleUuidPath(parentModuleUuidPath + model.getUuid() + MODULE_UUID_PATH_SEPARATOR);
      } else if (Scopes.isProject(parentResource)) {
        // Directory
        model.setModuleUuid(parentResource.getUuid());
        String parentModuleUuidPath = parentModel.getModuleUuidPath();
        model.setModuleUuidPath(parentModuleUuidPath);
      } else {
        // File
        model.setModuleUuid(parentModel.getModuleUuid());
        String parentModuleUuidPath = parentModel.getModuleUuidPath();
        model.setModuleUuidPath(parentModuleUuidPath);
      }
    }
  }

  static void mergeModel(ResourceModel model, Resource resource) {
    model.setEnabled(true);
    model.setKey(resource.getEffectiveKey());
    if (StringUtils.isNotBlank(resource.getName())) {
      model.setName(resource.getName());
    }
    if (StringUtils.isNotBlank(resource.getLongName())) {
      model.setLongName(resource.getLongName());
    }
    if (StringUtils.isNotBlank(resource.getDescription())) {
      model.setDescription(resource.getDescription());
    }
    if (StringUtils.isNotBlank(resource.getPath())) {
      model.setPath(resource.getPath());
    }
    if (!ResourceUtils.isLibrary(resource)) {
      model.setScope(resource.getScope());
      model.setQualifier(resource.getQualifier());
    }
    Language language = resource.getLanguage();
    if (language != null) {
      model.setLanguageKey(language.getKey());
    }
  }
}
