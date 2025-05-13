package org.xmlresolver.catalog.query;

import org.xmlresolver.CatalogManager;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.catalog.entry.*;
import org.xmlresolver.logging.ResolverLogger;
import org.xmlresolver.utils.URIUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class QueryUri extends QueryCatalog {
    public final String uri;
    public final String nature;
    public final String purpose;

    public QueryUri(String uri, String nature, String purpose) {
        super();
        this.uri = uri;
        this.nature = nature;
        this.purpose = purpose;
    }

    public QueryUri(String uri) {
        super();
        this.uri = uri;
        this.nature = null;
        this.purpose = null;
    }

    @Override
    protected QueryResult lookup(CatalogManager manager, EntryCatalog catalog) {
        String compareUri = manager.normalizedForComparison(uri);
        String compareNature = manager.normalizedForComparison(nature);
        String comparePurpose = manager.normalizedForComparison(purpose);

        // <uri>
        for (Entry raw : catalog.entries(Entry.Type.URI)) {
            EntryUri entry = (EntryUri) raw;
            if (compareUri.equals(manager.normalizedForComparison(entry.name))
                    && (nature == null || entry.nature == null
                    || compareNature.equals(manager.normalizedForComparison(entry.nature)))
                    && (purpose == null || entry.purpose == null
                    || comparePurpose.equals(manager.normalizedForComparison(entry.purpose)))) {
                return new QueryResult(entry.uri);
            }
        }

        // <rewriteURI>
        EntryRewriteUri rewrite = null;
        String rewriteStart = null;
        for (Entry raw : catalog.entries(Entry.Type.REWRITE_URI)) {
            EntryRewriteUri entry = (EntryRewriteUri) raw;
            String compareStart = manager.normalizedForComparison(entry.uriStart);
            if (compareUri.startsWith(compareStart)) {
                if (rewrite == null || compareStart.length() > rewriteStart.length()) {
                    rewrite = entry;
                    rewriteStart = compareStart;
                }
            }
        }
        if (rewrite != null) {
            try {
                URI resolved = new URI(rewrite.rewritePrefix.toString() + compareUri.substring(rewriteStart.length()));
                return new QueryResult(resolved);
            } catch (URISyntaxException ex) {
                ResolverLogger logger = manager.getResolverConfiguration().getFeature(ResolverFeature.RESOLVER_LOGGER);
                logger.debug("Invalid URI syntax resolving rewriteUri: " + ex.getMessage());
                return null;
            }
        }

        // <uriSuffix>
        EntryUriSuffix suffix = null;
        String uriSuffix = null;
        for (Entry raw : catalog.entries(Entry.Type.URI_SUFFIX)) {
            EntryUriSuffix entry = (EntryUriSuffix) raw;
            String compareSuffix = manager.normalizedForComparison(entry.uriSuffix);
            if (compareUri.endsWith(compareSuffix)) {
                if (suffix == null || compareSuffix.length() > uriSuffix.length()) {
                    suffix = entry;
                    uriSuffix = compareSuffix;
                }
            }
        }
        if (suffix != null) {
            return new QueryResult(suffix.uri);
        }

        // <delegateUri>
        ArrayList<EntryDelegateUri> delegated = new ArrayList<>();
        for (Entry raw : catalog.entries(Entry.Type.DELEGATE_URI)) {
            EntryDelegateUri entry = (EntryDelegateUri) raw;
            String delegateStart = manager.normalizedForComparison(entry.uriStart);
            if (compareUri.startsWith(delegateStart)) {
                int pos = 0;
                while (pos < delegated.size()
                        && delegateStart.length() <= manager.normalizedForComparison(delegated.get(pos).uriStart).length()) {
                    pos += 1;
                }
                delegated.add(pos, entry);
            }
        }
        if (!delegated.isEmpty()) {
            ArrayList<URI> catalogs = new ArrayList<>();
            for (EntryDelegateUri entry : delegated) {
                catalogs.add(entry.catalog);
            }
            return new QueryDelegateUri(uri, nature, purpose, catalogs);
        }

        return QueryResult.EMPTY_RESULT;
    }
}
