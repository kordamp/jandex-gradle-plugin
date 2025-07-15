package org.kordamp.gradle.plugin.jandex.internal

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

class BooleanState implements Serializable {
    private static final long serialVersionUID = 1L

    private Boolean value
    private transient Provider<Boolean> provider
    private Boolean providerValue // Serializable cache of provider value

    BooleanState() {}

    BooleanState(ProviderFactory providers) {
        // For cases where we need provider support
    }

    void set(Boolean value) {
        this.value = value
        this.provider = null
        this.providerValue = null
    }

    void set(Provider<Boolean> provider) {
        this.value = null
        this.provider = provider
        // Resolve and cache the value for serialization
        this.providerValue = provider.getOrNull()
    }

    Boolean get() {
        if (value != null) return value
        if (provider != null) return provider.get()
        return providerValue // Use cached value if provider is transient
    }

    Boolean getOrNull() {
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
