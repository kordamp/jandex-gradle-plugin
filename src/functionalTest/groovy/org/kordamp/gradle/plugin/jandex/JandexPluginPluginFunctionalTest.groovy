/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2024 Andres Almiray.
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
}
