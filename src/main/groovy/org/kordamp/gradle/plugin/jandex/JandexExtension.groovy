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

import groovy.transform.CompileStatic
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * @author Andres Almiray
 */
@CompileStatic
interface JandexExtension {
    Property<String> getVersion()

    Property<Integer> getIndexVersion()

    /**
     * Additional task names or patterns that should depend on the jandex task.
     * The plugin already sets up dependencies for 'compileTestJava', 'jar', and Javadoc tasks.
     * This property allows adding more tasks that should depend on the jandex task.
     * You can specify exact task names or patterns using Ant-style patterns (e.g., '*Test').
     * @return a list of task names or patterns
     */
    ListProperty<String> getAdditionalDependentTasks()
}
