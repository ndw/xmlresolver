package org.xmlresolver;

import java.net.URI;

/**
 * Response to a catalog query.
 * <p>The response includes the resource URI from the catalog (if the request was satisfied by the query).
 * The {@code found} field (or {@link #isResolved()} method) determine if the request was successful.</p>
 */
public class CatalogResponse {
    /**
     * The resource request that generated this response.
     */
    public final ResourceRequest request;
    /**
     * True if and only if a matching catalog entry was found.
     */
    public final boolean found;
    /**
     * The URI found in the catalog.
     */
    public final URI uri;

    protected CatalogResponse(ResourceRequest request) {
        this.request = request;
        this.uri = null;
        this.found = false;
    }

    protected CatalogResponse(ResourceRequest request, URI uri) {
        this.request = request;
        this.uri = uri;
        this.found = uri != null;
    }

    /**
     * Was the query successful?
     * <p>This is a convenience method providing the same API method as {@link ResourceResponse}.
     * It simply returns the {@link #found} field.</p>
     * @return true if the query found an entry
     */
    public boolean isResolved() {
        return found;
    }
}
