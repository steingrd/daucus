package io.trygvis.esper.testing.nexus;

import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Lists.*;
import static io.trygvis.esper.testing.nexus.ArtifactXml.repositoryFilter;
import junit.framework.*;

import java.io.*;
import java.util.*;

public class TestXmlParsing extends TestCase {
    public void testProjectParsing() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/nexus/search-1.xml")) {
            ArtifactSearchResult result = NexusParser.parseDocument(stream);

            List<ArtifactXml> list = result.artifacts;

            assertNotNull(list);
            assertEquals(132, list.size());

            ArtifactXml artifact = list.get(0);
            assertEquals("org.codehaus.mojo.hibernate3", artifact.groupId);
            assertEquals("maven-hibernate3-jdk15", artifact.artifactId);
            assertEquals("2.0-alpha-1", artifact.version);

            artifact = list.get(4);
            assertEquals("org.codehaus.mojo.hibernate3", artifact.groupId);
            assertEquals("maven-hibernate3", artifact.artifactId);
            assertEquals("2.0-alpha-1", artifact.version);
            assertEquals(2, artifact.hits.size());
            assertEquals("appfuse-releases", artifact.hits.get(0).repositoryId);
            assertEquals(1, artifact.hits.get(0).files.size());
            assertTrue(artifact.hits.get(0).files.get(0).classifier.isNone());
            assertEquals("pom", artifact.hits.get(0).files.get(0).extension);

            assertEquals(2, artifact.hits.size());
            ArrayList<ArtifactXml> filtered = newArrayList(filter(list, repositoryFilter("appfuse-releases")));
            assertEquals(5, filtered.size());

            FlatArtifact flatArtifact = filtered.get(0).flatten("appfuse-releases");
            assertEquals("org.codehaus.mojo.hibernate3", flatArtifact.groupId);
            assertEquals("maven-hibernate3-jdk15", flatArtifact.artifactId);
            assertEquals("2.0-alpha-1", flatArtifact.version);
            assertEquals(2, flatArtifact.files.size());
        }
    }
}