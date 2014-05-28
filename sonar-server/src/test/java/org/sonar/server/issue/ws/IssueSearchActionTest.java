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

package org.sonar.server.issue.ws;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.component.Component;
import org.sonar.api.i18n.I18n;
import org.sonar.api.issue.ActionPlan;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueFinder;
import org.sonar.api.issue.IssueQuery;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.Rule;
import org.sonar.api.user.User;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.Duration;
import org.sonar.api.utils.Durations;
import org.sonar.api.utils.Paging;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.issue.DefaultActionPlan;
import org.sonar.core.issue.DefaultIssueQueryResult;
import org.sonar.core.issue.workflow.Transition;
import org.sonar.core.user.DefaultUser;
import org.sonar.server.issue.ActionService;
import org.sonar.server.issue.IssueService;
import org.sonar.server.user.MockUserSession;
import org.sonar.server.user.UserSession;
import org.sonar.server.ws.WsTester;

import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class IssueSearchActionTest {

  @Mock
  IssueFinder issueFinder;

  @Mock
  IssueService issueService;

  @Mock
  ActionService actionService;

  @Mock
  I18n i18n;

  @Mock
  Durations durations;

  List<Issue> issues;
  DefaultIssueQueryResult result;

  Date issueCreationDate;

  WsTester tester;

  @Before
  public void setUp() throws Exception {
    issues = new ArrayList<Issue>();
    result = new DefaultIssueQueryResult(issues);
    when(issueFinder.find(any(IssueQuery.class))).thenReturn(result);

    issueCreationDate = DateUtils.parseDateTime("2014-01-22T19:10:03+0100");
    when(i18n.formatDateTime(any(Locale.class), eq(issueCreationDate))).thenReturn("Jan 22, 2014 10:03 AM");

    result.setMaxResultsReached(true);
    result.setPaging(Paging.create(100, 1, 2));
    when(i18n.formatInteger(any(Locale.class), eq(2))).thenReturn("2");

    tester = new WsTester(new IssuesWs(mock(IssueShowAction.class), new IssueSearchAction(issueFinder, new IssueActionsWriter(issueService, actionService), i18n, durations)));
  }

  @Test
  public void issues() throws Exception {
    String issueKey = "ABCD";
    Issue issue = new DefaultIssue()
      .setKey(issueKey)
      .setComponentKey("sample:src/main/xoo/sample/Sample.xoo")
      .setProjectKey("sample")
      .setRuleKey(RuleKey.of("squid", "AvoidCycle"))
      .setActionPlanKey("AP-ABCD")
      .setLine(12)
      .setEffortToFix(2.0)
      .setMessage("Fix it")
      .setResolution("FIXED")
      .setStatus("CLOSED")
      .setSeverity("MAJOR")
      .setAssignee("john")
      .setReporter("steven")
      .setAuthorLogin("Henry")
      .setCreationDate(issueCreationDate);
    issues.add(issue);

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues.json");
  }

  @Test
  public void issues_with_components() throws Exception {
    String issueKey = "ABCD";
    Issue issue = new DefaultIssue()
      .setKey(issueKey)
      .setComponentKey("sample:src/main/xoo/sample/Sample.xoo")
      .setProjectKey("sample")
      .setRuleKey(RuleKey.of("squid", "AvoidCycle"))
      .setActionPlanKey("AP-ABCD")
      .setLine(12)
      .setEffortToFix(2.0)
      .setMessage("Fix it")
      .setResolution("FIXED")
      .setStatus("CLOSED")
      .setSeverity("MAJOR")
      .setAssignee("john")
      .setReporter("steven")
      .setAuthorLogin("Henry")
      .setCreationDate(issueCreationDate);
    issues.add(issue);

    ComponentDto component = new ComponentDto()
      .setId(10L)
      .setKey("sample:src/main/xoo/sample/Sample.xoo")
      .setLongName("src/main/xoo/sample/Sample.xoo")
      .setName("Sample.xoo")
      .setQualifier("FIL")
      .setPath("src/main/xoo/sample/Sample.xoo")
      .setSubProjectId(7L)
      .setProjectId(7L);

    ComponentDto project = new ComponentDto()
      .setId(7L)
      .setKey("sample")
      .setLongName("Sample")
      .setName("Sample")
      .setQualifier("TRK")
      .setProjectId(7L);

    result.addComponents(Lists.<Component>newArrayList(component, project));
    result.addProjects(Lists.<Component>newArrayList(project));

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_components.json");
  }

  @Test
  public void issues_with_rules() throws Exception {
    String issueKey = "ABCD";
    Issue issue = new DefaultIssue()
      .setKey(issueKey)
      .setComponentKey("sample:src/main/xoo/sample/Sample.xoo")
      .setProjectKey("sample")
      .setRuleKey(RuleKey.of("squid", "AvoidCycle"))
      .setActionPlanKey("AP-ABCD")
      .setLine(12)
      .setEffortToFix(2.0)
      .setMessage("Fix it")
      .setResolution("FIXED")
      .setStatus("CLOSED")
      .setSeverity("MAJOR")
      .setAssignee("john")
      .setReporter("steven")
      .setAuthorLogin("Henry")
      .setCreationDate(issueCreationDate);
    issues.add(issue);

    result.addRules(newArrayList(
      Rule.create("squid", "AvoidCycle").setName("Avoid cycle").setDescription("Avoid cycle description")
    ));

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_rules.json");
  }

  @Test
  public void issues_with_users() throws Exception {
    String issueKey = "ABCD";
    Issue issue = new DefaultIssue()
      .setKey(issueKey)
      .setComponentKey("sample:src/main/xoo/sample/Sample.xoo")
      .setProjectKey("sample")
      .setRuleKey(RuleKey.of("squid", "AvoidCycle"))
      .setActionPlanKey("AP-ABCD")
      .setLine(12)
      .setEffortToFix(2.0)
      .setMessage("Fix it")
      .setResolution("FIXED")
      .setStatus("CLOSED")
      .setSeverity("MAJOR")
      .setAssignee("john")
      .setReporter("steven")
      .setAuthorLogin("Henry")
      .setCreationDate(issueCreationDate);
    issues.add(issue);

    result.addUsers(Lists.<User>newArrayList(
      new DefaultUser().setName("John").setLogin("john").setActive(true).setEmail("john@email.com")
    ));

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_users.json");
  }

  @Test
  public void issues_with_dates() throws Exception {
    Date creationDate = DateUtils.parseDateTime("2014-01-22T19:10:03+0100");
    Date updateDate = DateUtils.parseDateTime("2014-01-23T19:10:03+0100");
    Date closedDate = DateUtils.parseDateTime("2014-01-24T19:10:03+0100");

    Issue issue = createStandardIssue()
      .setCreationDate(creationDate)
      .setUpdateDate(updateDate)
      .setCloseDate(closedDate);
    issues.add(issue);

    when(i18n.formatDateTime(any(Locale.class), eq(creationDate))).thenReturn("Jan 22, 2014 10:03 AM");
    when(i18n.formatDateTime(any(Locale.class), eq(updateDate))).thenReturn("Jan 23, 2014 10:03 AM");
    when(i18n.ageFromNow(any(Locale.class), eq(updateDate))).thenReturn("9 days");
    when(i18n.formatDateTime(any(Locale.class), eq(closedDate))).thenReturn("Jan 24, 2014 10:03 AM");

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_dates.json");
  }

  @Test
  public void issues_with_debt() throws Exception {
    Duration debt = (Duration.create(7260L));
    Issue issue = createStandardIssue().setDebt(debt);
    issues.add(issue);

    when(durations.format(any(Locale.class), eq(debt), eq(Durations.DurationFormat.SHORT))).thenReturn("2 hours 1 minutes");

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_debt.json");
  }

  @Test
  public void issues_with_action_plan() throws Exception {
    Issue issue = createStandardIssue()
      .setActionPlanKey("AP-ABCD");
    issues.add(issue);

    MockUserSession.set();
    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_action_plan.json");
  }

  @Test
  public void issues_with_attributes() throws Exception {
    Issue issue = createStandardIssue()
      .setAttribute("jira-issue-key", "SONAR-1234");
    issues.add(issue);

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search");
    request.execute().assertJson(getClass(), "issues_with_attributes.json");
  }

  @Test
  public void issues_with_extra_fields() throws Exception {
    Issue issue = createStandardIssue()
      .setActionPlanKey("AP-ABCD")
      .setAssignee("john");
    issues.add(issue);

    MockUserSession.set().setLogin("john");
    when(issueService.listTransitions(eq(issue), any(UserSession.class))).thenReturn(newArrayList(Transition.create("reopen", "RESOLVED", "REOPEN")));

    result.addActionPlans(newArrayList((ActionPlan) new DefaultActionPlan().setKey("AP-ABCD").setName("1.0")));

    result.addUsers(Lists.<User>newArrayList(
      new DefaultUser().setName("John").setLogin("john")
    ));

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search").setParam("extra_fields", "actions,transitions,assigneeName,actionPlanName");
    request.execute().assertJson(getClass(), "issues_with_extra_fields.json");
  }

  @Test
  public void verify_issue_query_parameters() throws Exception {
    Map<String, String> map = newHashMap();
    map.put("issues", "ABCDE1234");
    map.put("severities", "MAJOR,MINOR");
    map.put("statuses", "CLOSED");
    map.put("resolutions", "FALSE-POSITIVE");
    map.put("resolved", "true");
    map.put("components", "org.apache");
    map.put("componentRoots", "org.sonar");
    map.put("reporters", "marilyn");
    map.put("assignees", "joanna");
    map.put("languages", "xoo");
    map.put("assigned", "true");
    map.put("planned", "true");
    map.put("hideRules", "true");
    map.put("createdAt", "2013-04-15T09:08:24+0200");
    map.put("createdAfter", "2013-04-16T09:08:24+0200");
    map.put("createdBefore", "2013-04-17T09:08:24+0200");
    map.put("rules", "squid:AvoidCycle,findbugs:NullReference");
    map.put("pageSize", "10");
    map.put("pageIndex", "50");
    map.put("sort", "CREATION_DATE");
    map.put("asc", "true");

    WsTester.TestRequest request = tester.newGetRequest("api/issues", "search").setParams(map);
    request.execute();

    ArgumentCaptor<IssueQuery> captor = ArgumentCaptor.forClass(IssueQuery.class);
    verify(issueFinder).find(captor.capture());

    IssueQuery query = captor.getValue();
    assertThat(query.requiredRole()).isEqualTo("user");
    assertThat(query.issueKeys()).containsOnly("ABCDE1234");
    assertThat(query.severities()).containsOnly("MAJOR", "MINOR");
    assertThat(query.statuses()).containsOnly("CLOSED");
    assertThat(query.resolutions()).containsOnly("FALSE-POSITIVE");
    assertThat(query.resolved()).isTrue();
    assertThat(query.components()).containsOnly("org.apache");
    assertThat(query.componentRoots()).containsOnly("org.sonar");
    assertThat(query.reporters()).containsOnly("marilyn");
    assertThat(query.assignees()).containsOnly("joanna");
    assertThat(query.languages()).containsOnly("xoo");
    assertThat(query.assigned()).isTrue();
    assertThat(query.planned()).isTrue();
    assertThat(query.hideRules()).isTrue();
    assertThat(query.createdAt()).isEqualTo(DateUtils.parseDateTime("2013-04-15T09:08:24+0200"));
    assertThat(query.createdAfter()).isEqualTo(DateUtils.parseDateTime("2013-04-16T09:08:24+0200"));
    assertThat(query.createdBefore()).isEqualTo(DateUtils.parseDateTime("2013-04-17T09:08:24+0200"));
    assertThat(query.rules()).containsOnly(RuleKey.of("squid", "AvoidCycle"), RuleKey.of("findbugs", "NullReference"));
    assertThat(query.pageSize()).isEqualTo(10);
    assertThat(query.pageIndex()).isEqualTo(50);
    assertThat(query.sort()).isEqualTo("CREATION_DATE");
    assertThat(query.asc()).isTrue();
  }

  private DefaultIssue createStandardIssue() {
    return createIssue();
  }

  private DefaultIssue createIssue() {
    return new DefaultIssue()
      .setKey("ABCD")
      .setComponentKey("sample:src/main/xoo/sample/Sample.xoo")
      .setProjectKey("sample")
      .setRuleKey(RuleKey.of("squid", "AvoidCycle"))
      .setCreationDate(issueCreationDate);
  }

}