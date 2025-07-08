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
package org.kordamp.gradle.plugin.jandex;

import org.gradle.api.Project;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Andres Almiray
 */
public abstract class Banner implements BuildService<Banner.Params> {
    private static final String ORG_KORDAMP_BANNER = "org.kordamp.banner";

    private String productVersion;
    private String productId;
    private final List<String> projectNames = new ArrayList<>();

    interface Params extends BuildServiceParameters {
    }

    public void display(Project project) {
        if (checkIfVisited(project)) return;

        ResourceBundle bundle = ResourceBundle.getBundle(Banner.class.getName());
        productVersion = bundle.getString("product.version");
        productId = bundle.getString("product.id");
        String productName = bundle.getString("product.name");
        String banner = MessageFormat.format(bundle.getString("product.banner"), productName, productVersion);

        boolean printBanner = null == System.getProperty(ORG_KORDAMP_BANNER) || Boolean.getBoolean(ORG_KORDAMP_BANNER);

        // Only print the banner, don't read or write any files to avoid configuration cache issues
        if (printBanner) {
            System.err.println(banner);
        }
    }

    private boolean checkIfVisited(Project project) {
        if (projectNames.contains(project.getRootProject().getName())) {
            return true;
        }
        projectNames.add(project.getRootProject().getName());
        return false;
    }
}
