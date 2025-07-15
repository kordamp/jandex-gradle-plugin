package org.kordamp.gradle.plugin.jandex.internal

import java.io.File
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.RegularFileProperty

class FileState implements Serializable {
    private static final long serialVersionUID = 1L

    private File value
    private transient Provider<File> provider
    private File providerValue // Serializable cache of provider value

    FileState() {}

    FileState(ProviderFactory providers) {
        // For cases where we need provider support
    }

    void set(File value) {
        this.value = value
        this.provider = null
        this.providerValue = null
    }

    void set(Provider<File> provider) {
        this.value = null
        this.provider = provider
        // Resolve and cache the value for serialization
        this.providerValue = provider.getOrNull()
    }

    void set(RegularFileProperty fileProperty) {
        this.value = null
        this.provider = fileProperty.asFile
        // Resolve and cache the value for serialization
        this.providerValue = provider.getOrNull()
    }

    File get() {
        if (value != null) return value
        if (provider != null) return provider.get()
        return providerValue // Use cached value if provider is transient
    }

    File getOrNull() {
        if (value != null) return value
        if (provider != null) return provider.getOrNull()
        return providerValue
    }

    // For CC compatibility - resolve any providers before serialization
    private void writeObject(ObjectOutputStream out) throws IOException {
        // Ensure providerValue is up to date
        if (provider != null && providerValue == null) {
            providerValue = provider.getOrNull()
        }
        out.defaultWriteObject()
    }
}
