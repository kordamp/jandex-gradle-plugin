/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019 Andres Almiray.
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
package org.kordamp.gradle.wildfly

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.BasePlugin

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.pluginManager.withPlugin('java-base', new Action<AppliedPlugin>() {
            @Override
            void execute(AppliedPlugin appliedPlugin) {
                project.tasks.register('jandex', JandexTask,
                        new Action<JandexTask>() {
                            @Override
                            void execute(JandexTask t) {
                                t.dependsOn('classes')
                                t.group = BasePlugin.BUILD_GROUP
                                t.description = 'Generate a jandex index'
                            }
                        })
            }
        })
    }
}
