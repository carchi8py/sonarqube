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
package org.sonar.batch.design;

import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.batch.SonarIndex;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.graph.Cycle;
import org.sonar.graph.Edge;
import org.sonar.graph.IncrementalCyclesAndFESSolver;
import org.sonar.graph.MinimumFeedbackEdgeSetSolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DirectoryDsmDecorator extends DsmDecorator {

  public DirectoryDsmDecorator(SonarIndex index) {
    super(index);
  }

  @Override
  protected List<Resource> getChildren(Resource resource, DecoratorContext context) {
    List<DecoratorContext> fileContexts = context.getChildren();
    List<Resource> files = new ArrayList<>(fileContexts.size());
    for (DecoratorContext decoratorContext : fileContexts) {
      files.add(decoratorContext.getResource());
    }
    return files;
  }

  @Override
  protected Set<Edge> doProcess(List<Resource> children, DecoratorContext context) {
    IncrementalCyclesAndFESSolver<Resource> cycleDetector = new IncrementalCyclesAndFESSolver<>(getIndex(), children);
    Set<Cycle> cycles = cycleDetector.getCycles();

    MinimumFeedbackEdgeSetSolver solver = new MinimumFeedbackEdgeSetSolver(cycles);
    Set<Edge> feedbackEdges = solver.getEdges();
    int tangles = solver.getWeightOfFeedbackEdgeSet();

    savePositiveMeasure(context, CoreMetrics.FILE_CYCLES, cycles.size());
    savePositiveMeasure(context, CoreMetrics.FILE_FEEDBACK_EDGES, feedbackEdges.size());
    savePositiveMeasure(context, CoreMetrics.FILE_TANGLES, tangles);
    savePositiveMeasure(context, CoreMetrics.FILE_EDGES_WEIGHT, getEdgesWeight(children));

    return feedbackEdges;
  }

  @Override
  protected boolean shouldDecorateResource(Resource resource, DecoratorContext context) {
    return ResourceUtils.isDirectory(resource);
  }
}
