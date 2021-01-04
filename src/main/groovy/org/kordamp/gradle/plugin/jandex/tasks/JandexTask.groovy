/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2021 Andres Almiray.
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
import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.kordamp.gradle.property.BooleanState
import org.kordamp.gradle.property.SimpleBooleanState
import org.kordamp.gradle.property.SimpleStringState
import org.kordamp.gradle.property.StringState

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexTask extends DefaultTask {
    private final BooleanState processDefaultFileSet
    private final StringState indexName

    @OutputFile
    final RegularFileProperty destination = project.objects.fileProperty()
    private ConfigurableFileTree fileSets

    JandexTask() {
        processDefaultFileSet = SimpleBooleanState.of(this, 'jandex.process.default.file.set', true)
        indexName = SimpleStringState.of(this, 'jandex.index.name', 'jandex.idx')

        destination.convention(indexName.provider.map(new Transformer<RegularFile, String>() {
            @Override
            RegularFile transform(String s) {
                project.layout.buildDirectory.file('jandex/' + s).get()
            }
        }))
    }

    @Option(option = 'jandex-process-default-file-set', description = "Include the 'main' source set. Defaults to true")
    void setProcessDefaultFileSet(boolean value) { processDefaultFileSet.property.set(value) }

    @Option(option = 'jandex-index-name', description = "The name of the index file. Defaults to jandex.idx")
    void setIndexName(String value) { indexName.property.set(value) }

    @Internal
    Property<Boolean> getProcessDefaultFileSet() { processDefaultFileSet.property }

    @Input
    Provider<Boolean> getResolvedProcessDefaultFileSet() { processDefaultFileSet.provider }

    @Internal
    Property<String> getIndexName() { indexName.property }

    @Input
    Provider<String> getResolvedIndexName() { indexName.provider }

    @InputFiles
    @CompileDynamic
    FileTree getFileSets() {
        FileTree files = null
        if (resolvedProcessDefaultFileSet.get()) {
            SourceSetContainer sourceSets = (SourceSetContainer) project.extensions.findByType(SourceSetContainer)
            List<FileTree> fileTrees = sourceSets.findByName('main').output.files.collect({ File dir ->
                project.fileTree(dir: dir, include: '**/*.class')
            })
            files = fileTrees[1..-1].inject(fileTrees[0]) { a, b -> a + b }
        }
        if (this.@fileSets) {
            files = files ? files + this.@fileSets : this.@fileSets
        }
        files
    }

    void setFileSets(FileTree fileSets) {
        this.@fileSets = project.fileTree(fileSets)
    }

    @TaskAction
    void generateIndex() {
        JandexHelper.createIndex(project, getFileSets(), destination.get().asFile)
    }
}
