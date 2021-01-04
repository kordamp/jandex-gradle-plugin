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
package org.kordamp.gradle.plugin.jandex

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.kordamp.gradle.plugin.jandex.tasks.JandexTask

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexPlugin implements Plugin<Project> {
    void apply(Project project) {
        Banner.display(project)

        project.plugins.apply(JavaPlugin)

        Configuration jandexConfiguration = project.configurations.maybeCreate('jandex')
        jandexConfiguration.defaultDependencies(new Action<DependencySet>() {
            @Override
            void execute(DependencySet dependencies) {
                dependencies.add(project.dependencies.create('org.jboss:jandex:2.2.2.Final'))
            }
        })

        TaskProvider<JandexTask> jandex = project.tasks.register('jandex', JandexTask,
            new Action<JandexTask>() {
                @Override
                void execute(JandexTask t) {
                    t.dependsOn(project.tasks.named('classes'))
                    t.group = BasePlugin.BUILD_GROUP
                    t.description = 'Generate a jandex index'
                    t.classpath = jandexConfiguration
                }
            })

        project.tasks.named('classes').configure(new Action<Task>() {
            @Override
            void execute(Task t) {
                t.finalizedBy(jandex)
            }
        })

        project.tasks.named('processResources', Copy).configure(new Action<Copy>() {
            @Override
            @CompileDynamic
            void execute(Copy t) {
                jandex.get().destination.set(project.file("${t.destinationDir}/META-INF/jandex.idx"))
            }
        })
    }
}
