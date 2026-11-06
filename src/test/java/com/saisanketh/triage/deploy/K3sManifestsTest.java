package com.saisanketh.triage.deploy;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks deploy/k3s/*.yaml without a cluster (kubectl isn't installed on the build machine): the files parse, every object
 * is in the "triage" namespace, every secret key the app reads exists in the template, the template holds only
 * placeholders, and the app image tag matches the Maven project version.
 */
class K3sManifestsTest {

    private static final Path DIR = Path.of("deploy/k3s");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objects(String fileName) throws IOException {
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Object doc : new Yaml().loadAll(Files.readString(DIR.resolve(fileName)))) {
            if (doc != null) {
                docs.add((Map<String, Object>) doc);
            }
        }
        return docs;
    }

    private static List<Map<String, Object>> allObjects() throws IOException {
        List<Map<String, Object>> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                all.addAll(objects(f.getFileName().toString()));
            }
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void everyObjectParsesAndLivesInTheTriageNamespace() throws IOException {
        List<Map<String, Object>> all = allObjects();

        assertThat(all).extracting(o -> o.get("kind")).contains("Namespace", "StatefulSet", "Deployment", "Service",
                "Ingress", "Secret");
        for (Map<String, Object> o : all) {
            Map<String, Object> metadata = map(o.get("metadata"));
            if (!"Namespace".equals(o.get("kind"))) {
                assertThat(metadata.get("namespace")).as(o.get("kind") + " " + metadata.get("name")).isEqualTo("triage");
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAppOnlyReadsSecretKeysThatTheTemplateHasAndTheImageMatchesThePom() throws IOException {
        Map<String, Object> secret = objects("secret.example.yaml").get(0);
        Set<String> templateKeys = map(secret.get("stringData")).keySet();

        Map<String, Object> deployment = objects("30-app.yaml").stream()
                .filter(o -> "Deployment".equals(o.get("kind"))).findFirst().orElseThrow();
        Map<String, Object> container = (Map<String, Object>) ((List<Object>) map(map(map(deployment.get("spec"))
                .get("template")).get("spec")).get("containers")).get(0);
        Set<String> usedKeys = new HashSet<>();
        for (Object e : (List<Object>) container.get("env")) {
            Object from = map(e).get("valueFrom");
            if (from != null) {
                usedKeys.add((String) map(map(from).get("secretKeyRef")).get("key"));
            }
        }
        assertThat(templateKeys).containsAll(usedKeys);

        String pom = Files.readString(Path.of("pom.xml"));
        String version = pom.split("<artifactId>issue-triage-assistant</artifactId>")[1].split("<version>")[1].split("<")[0];
        assertThat(container.get("image")).isEqualTo("docker.io/library/issue-triage-assistant:" + version);
    }

    @Test
    void theSecretTemplateHoldsPlaceholdersOnly() throws IOException {
        Map<String, Object> secret = objects("secret.example.yaml").get(0);
        for (Map.Entry<String, Object> e : map(secret.get("stringData")).entrySet()) {
            String value = String.valueOf(e.getValue());
            assertThat(value).as(e.getKey()).matches("change-me.*|github_pat_\\.\\.\\.|sk-ant-\\.\\.\\.|123456");
        }
        assertThat(Files.exists(DIR.resolve("secret.yaml"))).as("a filled-in secret must never be in the repo").isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theIngressRoutesOnlyPublicPaths() throws IOException {
        Map<String, Object> ingress = objects("30-app.yaml").stream()
                .filter(o -> "Ingress".equals(o.get("kind"))).findFirst().orElseThrow();
        List<Object> rules = (List<Object>) map(ingress.get("spec")).get("rules");
        List<Object> paths = (List<Object>) map(map(rules.get(0)).get("http")).get("paths");
        List<String> routed = paths.stream().map(p -> (String) map(p).get("path")).toList();

        assertThat(routed).containsExactlyInAnyOrder("/webhooks/github", "/", "/index.html", "/app.html", "/guide.html", "/theme.css", "/reports", "/repos",
                "/settings", "/actuator/health");
        assertThat(routed).noneMatch(p -> p.startsWith("/backfill") || p.startsWith("/eval") || p.startsWith("/embeddings")
                || p.startsWith("/docs"));
    }
}
