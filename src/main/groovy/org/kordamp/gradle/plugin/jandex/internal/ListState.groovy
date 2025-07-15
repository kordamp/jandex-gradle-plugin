package org.kordamp.gradle.plugin.jandex.internal

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ListProperty

class ListState<T> implements Serializable {
    private static final long serialVersionUID = 1L

    private List<T> value = []
    private transient Provider<List<T>> provider
    private List<T> providerValue // Serializable cache of provider value

    ListState() {}

    ListState(ProviderFactory providers) {
        // For cases where we need provider support
    }

    void set(List<T> value) {
        this.value = value
        this.provider = null
        this.providerValue = null
    }

    void set(Provider<List<T>> provider) {
        this.value = null
        this.provider = provider
        // Resolve and cache the value for serialization
        this.providerValue = provider.getOrNull()
    }

    void set(ListProperty<T> listProperty) {
        this.value = null
        this.provider = listProperty
        // Resolve and cache the value for serialization
        this.providerValue = provider.getOrNull()
    }

    List<T> get() {
        if (value != null) return value
        if (provider != null) return provider.get()
        return providerValue ?: [] // Use cached value if provider is transient, default to empty list
    }

    List<T> getOrNull() {
        if (value != null) return value
        if (provider != null) return provider.getOrNull()
        return providerValue ?: [] // Default to empty list
    }

    void add(T item) {
        if (value == null) value = []
        value.add(item)
        // Clear provider since we're modifying the value directly
        this.provider = null
        this.providerValue = null
    }

    // For CC compatibility - resolve any providers before serialization
    private void writeObject(ObjectOutputStream out) throws IOException {
        // Ensure providerValue is up to date
        if (provider != null && providerValue == null) {
            providerValue = provider.getOrNull() ?: []
        }
        out.defaultWriteObject()
    }
}
