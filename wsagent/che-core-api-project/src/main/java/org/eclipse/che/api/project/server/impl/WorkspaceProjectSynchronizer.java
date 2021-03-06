/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.project.server.impl;

import static java.util.Collections.unmodifiableSet;
import static org.eclipse.che.api.project.server.impl.ProjectDtoConverter.asDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.Runtime;
import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.core.model.workspace.config.ProjectConfig;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.project.shared.RegisteredProject;
import org.eclipse.che.api.workspace.server.WorkspaceService;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WorkspaceProjectSynchronizer implements ProjectSynchronizer, WorkspaceKeeper {

  private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectSynchronizer.class);

  private final ProjectConfigRegistry projectConfigRegistry;
  private final HttpJsonRequestFactory httpJsonRequestFactory;
  private final WorkspaceSyncCommunication workspaceSyncCommunication;

  private final String apiEndpoint;
  private final String workspaceId;
  private final Runtime workspaceRuntime;

  @Inject
  public WorkspaceProjectSynchronizer(
      @Named("che.api") String apiEndpoint,
      HttpJsonRequestFactory httpJsonRequestFactory,
      WorkspaceSyncCommunication workspaceSyncCommunication,
      ProjectConfigRegistry projectConfigRegistry)
      throws ServerException {
    this.apiEndpoint = apiEndpoint;
    this.httpJsonRequestFactory = httpJsonRequestFactory;
    this.workspaceSyncCommunication = workspaceSyncCommunication;
    this.projectConfigRegistry = projectConfigRegistry;

    this.workspaceId = System.getenv("CHE_WORKSPACE_ID");

    LOG.info("Workspace ID: " + workspaceId);
    LOG.info("API Endpoint: " + apiEndpoint);

    // check connection
    workspaceRuntime = workspaceDto().getRuntime();
  }

  @Override
  public void synchronize() throws ServerException {

    List<ProjectConfigDto> remote = workspaceDto().getConfig().getProjects();

    // check on removed
    List<ProjectConfig> removed = new ArrayList<>();
    for (ProjectConfig r : remote) {
      if (!projectConfigRegistry.get(r.getPath()).isPresent()) {
        removed.add(r);
      }
    }

    for (ProjectConfig r : removed) {
      remove(r);
    }

    // update or add
    for (RegisteredProject project : projectConfigRegistry.getAll()) {

      if (!project.isSynced() && !project.isDetected()) {

        final ProjectConfig config =
            new NewProjectConfigImpl(
                project.getPath(),
                project.getType(),
                project.getMixins(),
                project.getName(),
                project.getDescription(),
                project.getPersistableAttributes(),
                null,
                project.getSource());

        boolean found = false;
        for (ProjectConfig r : remote) {
          if (r.getPath().equals(project.getPath())) {
            update(config);
            found = true;
          }
        }

        if (!found) {
          add(config);
        }

        project.setSynced(true);
      }
    }

    workspaceSyncCommunication.synchronizeWorkspace();
  }

  @Override
  public Set<ProjectConfig> getProjects() throws ServerException {

    WorkspaceConfig config = workspaceDto().getConfig();
    Set<ProjectConfig> projectConfigs = new HashSet<>(config.getProjects());

    return unmodifiableSet(projectConfigs);
  }

  private void add(ProjectConfig project) throws ServerException {
    final UriBuilder builder =
        UriBuilder.fromUri(apiEndpoint)
            .path(WorkspaceService.class)
            .path(WorkspaceService.class, "addProject");
    final String href = builder.build(workspaceId).toString();
    try {
      httpJsonRequestFactory.fromUrl(href).usePostMethod().setBody(asDto(project)).request();
    } catch (IOException | ApiException e) {
      throw new ServerException(e.getMessage());
    }
  }

  @Override
  public Runtime getRuntime() throws ServerException {
    return workspaceRuntime;
  }

  private void update(ProjectConfig project) throws ServerException {

    final UriBuilder builder =
        UriBuilder.fromUri(apiEndpoint)
            .path(WorkspaceService.class)
            .path(WorkspaceService.class, "updateProject");
    final String href =
        builder.build(new String[] {workspaceId, project.getPath()}, false).toString();
    try {
      httpJsonRequestFactory.fromUrl(href).usePutMethod().setBody(asDto(project)).request();
    } catch (IOException | ApiException e) {
      throw new ServerException(e.getMessage());
    }
  }

  private void remove(ProjectConfig project) throws ServerException {

    final UriBuilder builder =
        UriBuilder.fromUri(apiEndpoint)
            .path(WorkspaceService.class)
            .path(WorkspaceService.class, "deleteProject");

    final String href =
        builder.build(new String[] {workspaceId, project.getPath()}, false).toString();
    try {
      httpJsonRequestFactory.fromUrl(href).useDeleteMethod().request();
    } catch (IOException | ApiException e) {
      throw new ServerException(e.getMessage());
    }
  }

  /** @return WorkspaceDto */
  private WorkspaceDto workspaceDto() throws ServerException {

    final UriBuilder builder =
        UriBuilder.fromUri(apiEndpoint)
            .path(WorkspaceService.class)
            .path(WorkspaceService.class, "getByKey")
            .queryParam("includeInternalServers", "true");

    final String href = builder.build(workspaceId).toString();
    try {
      return httpJsonRequestFactory
          .fromUrl(href)
          .useGetMethod()
          .request()
          .asDto(WorkspaceDto.class);
    } catch (IOException | ApiException e) {
      throw new ServerException(e);
    }
  }
}
