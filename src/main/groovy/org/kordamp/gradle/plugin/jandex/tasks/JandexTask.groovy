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
package org.kordamp.gradle.plugin.jandex.tasks

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexTask extends DefaultTask {
    private final Property<Boolean> processDefaultFileSet
    private final Property<Boolean> includeInJar
    private final Property<String> indexName
    private final Property<Integer> indexVersion
    private final WorkerExecutor workerExecutor

    @Classpath
    final ConfigurableFileCollection classpathFiles

    @InputFiles
    final ConfigurableFileCollection sources

    @InputFiles
    final ConfigurableFileCollection mainClassesDirs

    @OutputFile
    final RegularFileProperty destination

    @Internal
    final DirectoryProperty processResourcesDir

    @Internal
    final Property<ProjectLayout> layout

    @Inject
    JandexTask(WorkerExecutor workerExecutor, ObjectFactory objects) {
        // Use proper logging instead of System.out.println
        def logger = org.gradle.api.logging.Logging.getLogger(JandexTask)
        logger.debug("JandexTask constructor called from " + this.getClass().getProtectionDomain().getCodeSource().getLocation())

        layout = objects.property(ProjectLayout)

        this.workerExecutor = workerExecutor

        // Create properties with support for command line flags, env vars, and system properties
        processDefaultFileSet = objects.property(Boolean)
        processDefaultFileSet.convention(true)
        // Check system property
        String sysPropProcessDefaultFileSet = System.getProperty("jandex.process.default.file.set")
        if (sysPropProcessDefaultFileSet != null) {
            processDefaultFileSet.set(Boolean.parseBoolean(sysPropProcessDefaultFileSet))
        }
        // Check environment variable
        String envProcessDefaultFileSet = System.getenv("JANDEX_PROCESS_DEFAULT_FILE_SET")
        if (envProcessDefaultFileSet != null) {
            processDefaultFileSet.set(Boolean.parseBoolean(envProcessDefaultFileSet))
        }

        includeInJar = objects.property(Boolean)
        includeInJar.convention(true)
        // Check system property
        String sysPropIncludeInJar = System.getProperty("jandex.include.in.jar")
        if (sysPropIncludeInJar != null) {
            includeInJar.set(Boolean.parseBoolean(sysPropIncludeInJar))
        }
        // Check environment variable
        String envIncludeInJar = System.getenv("JANDEX_INCLUDE_IN_JAR")
        if (envIncludeInJar != null) {
            includeInJar.set(Boolean.parseBoolean(envIncludeInJar))
        }

        indexName = objects.property(String)
        indexName.convention('jandex.idx')
        // Check system property
        String sysPropIndexName = System.getProperty("jandex.index.name")
        if (sysPropIndexName != null) {
            indexName.set(sysPropIndexName)
        }
        // Check environment variable
        String envIndexName = System.getenv("JANDEX_INDEX_NAME")
        if (envIndexName != null) {
            indexName.set(envIndexName)
        }

        indexVersion = objects.property(Integer)
        // Check system property
        String sysPropIndexVersion = System.getProperty("jandex.index.version")
        if (sysPropIndexVersion != null) {
            try {
                indexVersion.set(Integer.parseInt(sysPropIndexVersion))
            } catch (NumberFormatException ignored) {
                // Ignore invalid values
            }
        }
        // Check environment variable
        String envIndexVersion = System.getenv("JANDEX_INDEX_VERSION")
        if (envIndexVersion != null) {
            try {
                indexVersion.set(Integer.parseInt(envIndexVersion))
            } catch (NumberFormatException ignored) {
                // Ignore invalid values
            }
        }

        sources = objects.fileCollection()
        mainClassesDirs = objects.fileCollection()
        classpathFiles = objects.fileCollection()
        processResourcesDir = objects.directoryProperty()
        destination = objects.fileProperty()

        destination.convention(indexName.flatMap(new Transformer<Provider<RegularFile>, String>() {
            @Override
            Provider<RegularFile> transform(String s) {
                return includeInJar.flatMap(new Transformer<Provider<RegularFile>, Boolean>() {
                    @Override
                    Provider<RegularFile> transform(Boolean includeInJar) {
                        if (includeInJar) {
                            return processResourcesDir.file("META-INF/jandex.idx")
                        }
                        return layout.flatMap(new Transformer<Provider<RegularFile>, ProjectLayout>() {
                            @Override
                            Provider<RegularFile> transform(ProjectLayout l) {
                                return l.getBuildDirectory().file('jandex/' + s)
                            }
                        })
                    }
                })
            }
        }))
    }

    @Option(option = 'jandex-process-default-file-set', description = "Include the 'main' source set. Defaults to true")
    void setProcessDefaultFileSet(boolean value) { processDefaultFileSet.set(value) }

    @Option(option = 'jandex-include-in-jar', description = "Include the generated index in the default JAR. Defaults to true")
    void setIncludeInJar(boolean value) { includeInJar.set(value) }

    @Option(option = 'jandex-index-name', description = "The name of the index file. Defaults to jandex.idx")
    void setIndexName(String value) { indexName.set(value) }

    @Option(option = 'jandex-index-version', description = "The version of the index file. Defaults to the latest version supported by the invoked Jandex version")
    void setIndexVersion(Integer value) { indexVersion.set(value) }

    @Internal
    Property<Boolean> getProcessDefaultFileSet() { processDefaultFileSet }

    @Internal
    Property<Boolean> getIncludeInJar() { includeInJar }

    @Input
    Provider<Boolean> getResolvedProcessDefaultFileSet() { processDefaultFileSet }

    @Input
    Provider<Boolean> getResolvedIncludeInJar() { includeInJar }

    @Internal
    Property<String> getIndexName() { indexName }

    @Input
    @Optional
    Property<Integer> getIndexVersion() { indexVersion }

    @Input
    Provider<String> getResolvedIndexName() { indexName }

    @TaskAction
    @CompileDynamic
    void generateIndex() {
        WorkQueue workQueue = workerExecutor.classLoaderIsolation(new Action<ClassLoaderWorkerSpec>() {
            @Override
            void execute(ClassLoaderWorkerSpec classLoaderWorkerSpec) {
                classLoaderWorkerSpec.classpath.from(classpathFiles)
            }
        })

        workQueue.submit(JandexWorkAction, new Action<JandexWorkParameters>() {
            @Override
            void execute(JandexWorkParameters parameters) {
                parameters.sources.set(resolveSources())
                parameters.destination.set(destination)
                parameters.indexVersion.set(indexVersion)
            }
        })
    }

    private List<String> resolveSources() {
        List<String> files = []

        if (resolvedProcessDefaultFileSet.get()) {
            files.addAll((Collection<String>) mainClassesDirs.files*.absolutePath.flatten())
        }

        files.addAll((Collection<String>) sources.files*.absolutePath.flatten())

        files
    }
}
