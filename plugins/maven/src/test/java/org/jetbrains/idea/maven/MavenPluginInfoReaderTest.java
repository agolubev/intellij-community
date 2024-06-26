/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven;

import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MavenPluginInfoReaderTest extends MavenTestCase {
  private MavenPluginInfo p;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setRepositoryPath(new MavenCustomRepositoryHelper(getDir(), "plugins").getTestDataPath("plugins"));
    MavenId id = new MavenId("org.apache.maven.plugins", "maven-compiler-plugin", "2.0.2");
    p = ApplicationManager.getApplication().executeOnPooledThread(() -> MavenArtifactUtil.readPluginInfo(getRepositoryFile(), id))
      .get(10, TimeUnit.SECONDS);
  }

  public void testLoadingPluginInfo() {
    assertEquals("org.apache.maven.plugins", p.getGroupId());
    assertEquals("maven-compiler-plugin", p.getArtifactId());
    assertEquals("2.0.2", p.getVersion());
  }

  public void testGoals() {
    assertEquals("compiler", p.getGoalPrefix());

    List<String> qualifiedGoals = new ArrayList<>();
    List<String> displayNames = new ArrayList<>();
    List<String> goals = new ArrayList<>();
    for (MavenPluginInfo.Mojo m : p.getMojos()) {
      goals.add(m.getGoal());
      qualifiedGoals.add(m.getQualifiedGoal());
      displayNames.add(m.getDisplayName());
    }

    assertOrderedElementsAreEqual(goals, "compile", "testCompile");
    assertOrderedElementsAreEqual(qualifiedGoals,
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:compile",
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:testCompile");
    assertOrderedElementsAreEqual(displayNames, "compiler:compile", "compiler:testCompile");
  }
}
