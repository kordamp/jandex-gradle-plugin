/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2025 Andres Almiray.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.gradle.plugin.jandex

import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jboss.jandex.IndexReader
import org.junit.jupiter.api.Test

class JandexPluginPluginFunctionalTest  {

    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    private getSourceFileAClass() {
        new File(projectDir, 'src/main/java/com/sample').mkdirs()
        new File(projectDir, 'src/main/java/com/sample/AClass.java')
    }

    @Test
    void testApply() {
        projectDir = new File("build/functionalTestFixture/apply_${System.currentTimeMillis()}")
        projectDir.mkdirs()

        def indexFile = new File(projectDir, 'build/resources/main/META-INF/jandex.idx')
        Assertions.assertThat(indexFile).doesNotExist();

        settingsFile.text = ""
        buildFile.text = """
plugins {
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
}
"""

        sourceFileAClass.text = """
package com.sample;

public class AClass {
	public void sayHi() {
		System.out.println("hi");
	}
}
"""
        def runner1 = createRunner()
        def result1 = runner1.build()
        Assertions.assertThat(result1.task(':jandex').outcome).isEqualTo(TaskOutcome.SUCCESS);
        Assertions.assertThat(indexFile).exists();
        def content1 = indexFile.getText("UTF-8")
        Assertions.assertThat(content1).contains("sayHi");

        //Re run without any changes, to be sure the task is up-to-date:
        def runner2 = createRunner()
        def result2 = runner2.build()
        Assertions.assertThat(result2.task(':jandex').outcome).isEqualTo(TaskOutcome.UP_TO_DATE);

        //Modify source file, and verify jandex was executed again:
        sourceFileAClass.text = """
package com.sample;

public class AClass {
	public void sayHello() {
		System.out.println("hello");
	}
}
"""
        def runner3 = createRunner()
        def result3 = runner3.build()
        Assertions.assertThat(result3.task(':jandex').outcome).isEqualTo(TaskOutcome.SUCCESS);
        Assertions.assertThat(indexFile).exists();
        def content3 = indexFile.getText("UTF-8")
        Assertions.assertThat(content3)
            .isNotEqualTo(content1)
            .contains("sayHello");

        //Re run without any changes, to be sure the task is up-to-date:
        def runner4 = createRunner()
        def result4 = runner4.build()
        Assertions.assertThat(result4.task(':jandex').outcome).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void testIndexVersion() {
        projectDir = new File("build/functionalTestFixture/indexVersion_${System.currentTimeMillis()}")
        projectDir.mkdirs()

        def indexFile = new File(projectDir, 'build/resources/main/META-INF/jandex.idx')
        Assertions.assertThat(indexFile).doesNotExist();

        settingsFile.text = ""
        buildFile.text = """
plugins {
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
}

jandex {
    indexVersion = 2
}
"""

        sourceFileAClass.text = """
package com.sample;

public class AClass {
	public void sayHi() {
		System.out.println("hi");
	}
}
"""
        def runner1 = createRunner()
        def result1 = runner1.build()
        Assertions.assertThat(result1.task(':jandex').outcome).isEqualTo(TaskOutcome.SUCCESS);
        Assertions.assertThat(indexFile).exists()
        Assertions.assertThat(new IndexReader(indexFile.newInputStream()).indexVersion).isEqualTo(2)
        def content1 = indexFile.getText("UTF-8")
        Assertions.assertThat(content1).contains("AClass")

        //Re run without any changes, to be sure the task is up-to-date:
        def runner2 = createRunner()
        def result2 = runner2.build()
        Assertions.assertThat(result2.task(':jandex').outcome).isEqualTo(TaskOutcome.UP_TO_DATE);

        //Modify index version, and verify jandex was executed again:
        buildFile.text = """
plugins {
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
}

jandex {
    indexVersion = 9
}
"""
        def runner3 = createRunner()
        def result3 = runner3.build()
        Assertions.assertThat(result3.task(':jandex').outcome).isEqualTo(TaskOutcome.SUCCESS);
        Assertions.assertThat(indexFile).exists()
        Assertions.assertThat(new IndexReader(indexFile.newInputStream()).indexVersion).isEqualTo(9)
        def content3 = indexFile.getText("UTF-8")
        Assertions.assertThat(content3)
            .isNotEqualTo(content1)
            .contains("AClass")
            .contains("sayHi")

        //Re run without any changes, to be sure the task is up-to-date:
        def runner4 = createRunner()
        def result4 = runner4.build()
        Assertions.assertThat(result4.task(':jandex').outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    void testUnsupportedIndexVersion() {
        projectDir = new File("build/functionalTestFixture/unsupportedIndexVersion_${System.currentTimeMillis()}")
        projectDir.mkdirs()

        def indexFile = new File(projectDir, 'build/resources/main/META-INF/jandex.idx')
        Assertions.assertThat(indexFile).doesNotExist()

        settingsFile.text = ""
        buildFile.text = """
plugins {
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
}

jandex {
    indexVersion = 1000
}
"""

        sourceFileAClass.text = """
package com.sample;

public class AClass {
	public void sayHi() {
		System.out.println("hi");
	}
}
"""
        def result = createRunner().buildAndFail()
        Assertions.assertThat(result .task(':jandex').outcome).isEqualTo(TaskOutcome.FAILED);
        Assertions.assertThat(result .output)
                .contains("org.jboss.jandex.UnsupportedVersion")
                .contains("Can't write index version 1000; this IndexWriter only supports index versions")
    }

    def createRunner() {
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("jandex", "--stacktrace")
        runner.withProjectDir(projectDir)
        return runner
    }

    def createRunnerWithConfigurationCache() {
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("jandex", "--stacktrace", "--configuration-cache", "--debug")
        runner.withProjectDir(projectDir)
        return runner
    }

    @Test
    void testWithConfigurationCache() {
        projectDir = new File("build/functionalTestFixture/configCache_${System.currentTimeMillis()}")
        projectDir.mkdirs()

        def indexFile = new File(projectDir, 'build/resources/main/META-INF/jandex.idx')
        Assertions.assertThat(indexFile).doesNotExist();

        settingsFile.text = ""
        buildFile.text = """
plugins {
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
}
"""

        sourceFileAClass.text = """
package com.sample;

public class AClass {
	public void sayHi() {
		System.out.println("hi");
	}
}
"""
        // First run - configuration cache will be stored
        def runner1 = createRunnerWithConfigurationCache()
        try {
            System.out.println("[DEBUG_LOG] About to run first build with configuration cache");
            def result1 = runner1.build()
            System.out.println("[DEBUG_LOG] First build completed successfully");

            // Print the entire output for debugging
            System.out.println("[DEBUG_LOG] Build output: " + result1.output);

            // Check if the task ran successfully
            Assertions.assertThat(result1.task(':jandex')).isNotNull();
            Assertions.assertThat(result1.task(':jandex').outcome).isEqualTo(TaskOutcome.SUCCESS);

            // Check if the index file was created
            Assertions.assertThat(indexFile.exists()).isTrue();

            // Check if the configuration cache was used
            Assertions.assertThat(result1.output.contains("Configuration cache entry stored")).isTrue();

            System.out.println("[DEBUG_LOG] Configuration cache test passed for first run");

            // Second run - configuration cache should be reused
            def runner2 = createRunnerWithConfigurationCache()
            System.out.println("[DEBUG_LOG] About to run second build with configuration cache");
            def result2 = runner2.build()
            System.out.println("[DEBUG_LOG] Second build completed successfully");

            // Print the entire output for debugging
            System.out.println("[DEBUG_LOG] Build output: " + result2.output);

            // Check specifically for configuration cache messages
            System.out.println("[DEBUG_LOG] Configuration cache reused? " + result2.output.contains("Configuration cache entry reused"));
            System.out.println("[DEBUG_LOG] Configuration cache stored? " + result2.output.contains("Configuration cache entry stored"));

            // Look for any problems with the configuration cache
            if (result2.output.contains("problems were found storing the configuration cache")) {
                System.out.println("[DEBUG_LOG] Configuration cache problems found!");
                int problemsIndex = result2.output.indexOf("problems were found storing the configuration cache");
                int endIndex = Math.min(problemsIndex + 500, result2.output.length());
                System.out.println("[DEBUG_LOG] Problems: " + result2.output.substring(problemsIndex, endIndex));
            }

            // Check if the configuration cache was reused
            Assertions.assertThat(result2.output.contains("Configuration cache entry reused")).isTrue();

            System.out.println("[DEBUG_LOG] Configuration cache test passed for second run");
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Configuration cache test failed: " + e.getMessage());

            if (e instanceof org.gradle.testkit.runner.UnexpectedBuildFailure) {
                org.gradle.testkit.runner.UnexpectedBuildFailure buildFailure = (org.gradle.testkit.runner.UnexpectedBuildFailure) e;
                System.out.println("[DEBUG_LOG] Build output: " + buildFailure.getBuildResult().getOutput());
            }

            // Write the full error output to a file for inspection
            File errorFile = new File(projectDir, "error.log")
            errorFile.text = "Error message: " + e.getMessage() + "\n\n" + 
                            "Stack trace: " + e.getStackTrace().join("\n") + "\n\n" +
                            "Full output: " + e.toString()

            System.out.println("[DEBUG_LOG] Error details written to: " + errorFile.absolutePath);
            e.printStackTrace();
            throw e;
        }

        // For now, just test the first run to isolate the issue
    }
}
