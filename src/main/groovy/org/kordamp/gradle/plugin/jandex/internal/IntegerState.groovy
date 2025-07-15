package org.kordamp.gradle.plugin.jandex.internal

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

class IntegerState implements Serializable {
    private static final long serialVersionUID = 1L

    private Integer value
    private transient Provider<Integer> provider
    private Integer providerValue // Serializable cache of provider value

    IntegerState() {}

    IntegerState(ProviderFactory providers) {
        // For cases where we need provider support
    }

    void set(Integer value) {
        this.value = value
        this.provider = null
        this.providerValue = null
    }

    void set(Provider<Integer> provider) {
        this.value = null
        this.provider = provider
        // Resolve and cache the value for serialization
        this.providerValue = provider.getOrNull()
    }

    Integer get() {
        if (value != null) return value
        if (provider != null) return provider.get()
        return providerValue // Use cached value if provider is transient
    }

    Integer getOrNull() {
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
