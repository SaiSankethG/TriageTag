package com.saisanketh.triage.docs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Where each repo's reference docs come from (application.yml, {@code triage.docs.sources}).
 *
 * @param sources repo ("owner/name") -> where its docs are
 */
@ConfigurationProperties(prefix = "triage.docs")
public record DocsProperties(Map<String, Source> sources) {

    public DocsProperties {
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }

    /**
     * @param tag     the git tag to read, fixed so the docs match a release (e.g. "v4.1.2")
     * @param root    the Antora "modules" folder in the repo
     * @param modules which modules to load (each has a pages/ folder of .adoc files); "ROOT" is the top level
     * @param siteUrl      where those pages are published, e.g. "https://docs.spring.io/spring-boot/4.1"
     * @param excludePages pages to skip, as "module/page" without .adoc (e.g. "ROOT/redirect": only a list of anchors)
     */
    public record Source(String tag, String root, List<String> modules, String siteUrl, List<String> excludePages) {

        public Source {
            excludePages = excludePages == null ? List.of() : List.copyOf(excludePages);
        }
    }
}
