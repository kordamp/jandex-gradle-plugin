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
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
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

    private final ProviderFactory providerFactory

    @Inject
    JandexTask(WorkerExecutor workerExecutor, ObjectFactory objects, ProviderFactory providerFactory) {
        this.workerExecutor = workerExecutor
        this.providerFactory = providerFactory
        layout = objects.property(ProjectLayout)

        processDefaultFileSet = objects.property(Boolean)
        includeInJar = objects.property(Boolean)
        indexName = objects.property(String)
        indexVersion = objects.property(Integer)

        // Set final value by chaining providers with a defined precedence
        // 1. Command-line option (set via @Option)
        // 2. System property
        // 3. Environment variable
        // 4. Convention (default)
        processDefaultFileSet.convention(providerFactory.systemProperty('jandex.process.default.file.set')
                .orElse(providerFactory.environmentVariable('JANDEX_PROCESS_DEFAULT_FILE_SET'))
                .map({ Boolean.parseBoolean(it) })
                .orElse(true))

        includeInJar.convention(providerFactory.systemProperty('jandex.include.in.jar')
                .orElse(providerFactory.environmentVariable('JANDEX_INCLUDE_IN_JAR'))
                .map({ Boolean.parseBoolean(it) })
                .orElse(true))

        indexName.convention(providerFactory.systemProperty('jandex.index.name')
                .orElse(providerFactory.environmentVariable('JANDEX_INDEX_NAME'))
                .orElse('jandex.idx'))

        indexVersion.convention(providerFactory.systemProperty('jandex.index.version')
                .orElse(providerFactory.environmentVariable('JANDEX_INDEX_VERSION'))
                .map({ Integer.parseInt(it) }))

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
    void setProcessDefaultFileSet(boolean value) {
        processDefaultFileSet.set(value)
    }

    @Option(option = 'jandex-include-in-jar', description = "Include the generated index in the default JAR. Defaults to true")
    void setIncludeInJar(boolean value) {
        includeInJar.set(value)
    }

    @Option(option = 'jandex-index-name', description = "The name of the index file. Defaults to jandex.idx")
    void setIndexName(String value) {
        indexName.set(value)
    }

    @Option(option = 'jandex-index-version', description = "The version of the index file. Defaults to the latest version supported by the invoked Jandex version")
    void setIndexVersion(Integer value) {
        indexVersion.set(value)
    }

    @Input
    Provider<Boolean> getResolvedProcessDefaultFileSet() { 
        return processDefaultFileSet
    }

    @Input
    Provider<Boolean> getResolvedIncludeInJar() { 
        return includeInJar
    }

    @Input
    Property<String> getIndexName() { indexName }

    @Input
    @Optional
    Property<Integer> getIndexVersion() { indexVersion }

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