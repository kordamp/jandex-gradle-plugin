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
package org.kordamp.gradle.plugin.jandex.internal

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.kordamp.gradle.plugin.jandex.JandexExtension

import javax.inject.Inject

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexExtensionImpl implements JandexExtension {
    final Property<String> version

    @Inject
    JandexExtensionImpl(ObjectFactory objects) {
        version = objects.property(String).convention(DefaultVersions.INSTANCE.jandexVersion)
    }
}