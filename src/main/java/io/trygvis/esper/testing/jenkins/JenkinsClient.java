package io.trygvis.esper.testing.jenkins;

import fj.*;
import fj.data.*;
import io.trygvis.esper.testing.*;
import io.trygvis.esper.testing.jenkins.JenkinsJobXml.*;
import io.trygvis.esper.testing.util.*;
import org.apache.abdera.*;
import org.apache.abdera.model.*;
import org.apache.abdera.parser.*;
import org.codehaus.httpcache4j.*;
import org.codehaus.httpcache4j.cache.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.*;
import org.joda.time.DateTime;

import javax.xml.stream.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

import static fj.data.Option.*;
import static io.trygvis.esper.testing.Util.*;
import static org.apache.commons.lang.StringUtils.*;

public class JenkinsClient {
    private final XmlHttpClient xmlHttpClient;
    private final HttpClient<List<JenkinsEntryXml>> jenkinsEntryXmlClient;
    private final Parser parser;

    public JenkinsClient(HTTPCache http, Abdera abdera) {
        this.xmlHttpClient = new XmlHttpClient(http);
        this.parser = abdera.getParser();

        jenkinsEntryXmlClient = new HttpClient<>(http, new F<HTTPResponse, Option<List<JenkinsEntryXml>>>() {
            public Option<List<JenkinsEntryXml>> f(HTTPResponse response) {
                Feed feed = (Feed) parser.parse(response.getPayload().getInputStream()).getRoot();

                List<JenkinsEntryXml> list = new ArrayList<>();

                for (Entry entry : feed.getEntries()) {
                    try {
                        list.add(new JenkinsEntryXml(entry.getIdElement().getText(), new DateTime(entry.getPublished().getTime()), entry.getAlternateLinkResolvedHref().toURI()));
                    } catch (URISyntaxException ignore) {
                    }
                }

                return some(list);
            }
        });
    }

    public Option<List<JenkinsEntryXml>> fetchRss(URI uri) throws IOException {
        return jenkinsEntryXmlClient.fetch(uri);
    }

    public JenkinsXml fetchJobs(URI uri) throws XMLStreamException, JDOMException, IOException {
        Option<Document> d = xmlHttpClient.fetch(uri);

        if (d.isNone()) {
            Option<String> n = Option.none();

            return new JenkinsXml(n, n, n, Collections.<JenkinsJobEntryXml>emptyList());
        }

        Element root = d.some().getRootElement();

        List<JenkinsJobEntryXml> jobs = new ArrayList<>();
        for (Element job : root.getChildren("job")) {
            String name = trimToNull(job.getChildText("name"));
            String url = trimToNull(job.getChildText("url"));
            String color = trimToNull(job.getChildText("color"));

            if (name == null || url == null || color == null) {
                continue;
            }

            jobs.add(new JenkinsJobEntryXml(name, url, color));
        }

        return new JenkinsXml(
            Option.fromNull(root.getChildText("nodeName")),
            Option.fromNull(root.getChildText("nodeDescription")),
            Option.fromNull(root.getChildText("description")), jobs);
    }

    public Option<JenkinsJobXml> fetchJob(URI uri) throws IOException, JDOMException, XMLStreamException {
        Option<Document> d = xmlHttpClient.fetch(uri);

        if (d.isNone()) {
            return Option.none();
        }

        Element root = d.some().getRootElement();

        String name = root.getName();

        switch (name) {
            case "freeStyleProject":
                return some(JenkinsJobXml.parse(uri, JenkinsJobType.FREE_STYLE, root));
            case "mavenModuleSet":
                return some(JenkinsJobXml.parse(uri, JenkinsJobType.MAVEN, root));
            default:
                System.out.println("Unknown project type: " + name);
                return Option.none();
        }
    }
}

class JenkinsEntryXml {
    public final String id;
    public final DateTime timestamp;
    public final URI uri;

    JenkinsEntryXml(String id, DateTime timestamp, URI uri) {
        this.id = id;
        this.timestamp = timestamp;
        this.uri = uri;
    }
}

class JenkinsXml {
    public final Option<String> nodeName;
    public final Option<String> nodeDescription;
    public final Option<String> description;
    public final List<JenkinsJobEntryXml> jobs;

    JenkinsXml(Option<String> nodeName, Option<String> nodeDescription, Option<String> description, List<JenkinsJobEntryXml> jobs) {
        this.nodeName = nodeName;
        this.nodeDescription = nodeDescription;
        this.description = description;
        this.jobs = jobs;
    }
}

class JenkinsJobEntryXml {
    public final String name;
    public final String url;
    public final String color;

    JenkinsJobEntryXml(String name, String url, String color) {
        this.name = name;
        this.url = url;
        this.color = color;
    }
}

class JenkinsJobXml {
    enum JenkinsJobType {
        MAVEN, FREE_STYLE
    }

    public final JenkinsJobType type;
    public final Option<String> description;
    public final Option<String> displayName;
    public final Option<String> name;
    public final URI url;
    public final Option<String> color;
    public final boolean buildable;
    public final Option<BuildXml> lastBuild;
    public final Option<BuildXml> lastCompletedBuild;
    public final Option<BuildXml> lastFailedBuild;
    public final Option<BuildXml> lastSuccessfulBuild;
    public final Option<BuildXml> lastUnsuccessfulBuild;

    protected JenkinsJobXml(JenkinsJobType type, Option<String> description, Option<String> displayName,
                            Option<String> name, URI url, Option<String> color, boolean buildable,
                            Option<BuildXml> lastBuild, Option<BuildXml> lastCompletedBuild,
                            Option<BuildXml> lastFailedBuild, Option<BuildXml> lastSuccessfulBuild,
                            Option<BuildXml> lastUnsuccessfulBuild) {
        this.type = type;
        this.description = description;
        this.displayName = displayName;
        this.name = name;
        this.url = url;
        this.color = color;
        this.buildable = buildable;
        this.lastBuild = lastBuild;
        this.lastCompletedBuild = lastCompletedBuild;
        this.lastFailedBuild = lastFailedBuild;
        this.lastSuccessfulBuild = lastSuccessfulBuild;
        this.lastUnsuccessfulBuild = lastUnsuccessfulBuild;
    }

    static class BuildXml {
        public final int number;
        public final URI url;
        public static F<Element, Option<BuildXml>> buildXml = new F<Element, Option<BuildXml>>() {
            public Option<BuildXml> f(Element element) {
                Option<Integer> number = childText(element, "number").bind(Util.parseInt);
                Option<URI> url = childText(element, "url").bind(Util.parseUri);

                if (number.isNone() || url.isNone()) {
                    return Option.none();
                }

                return some(new BuildXml(number.some(), url.some()));
            }
        };

        BuildXml(int number, URI url) {
            this.number = number;
            this.url = url;
        }
    }

    public static JenkinsJobXml parse(URI uri, JenkinsJobType type, Element root) {
        return new JenkinsJobXml(type,
            childText(root, "description"),
            childText(root, "displayName"),
            childText(root, "name"),
            childText(root, "url").bind(Util.parseUri).orSome(uri),
            childText(root, "color"),
            childText(root, "buildable").bind(Util.parseBoolean).orSome(false),
            child(root, "lastBuild").bind(BuildXml.buildXml),
            child(root, "lastCompletedBuild").bind(BuildXml.buildXml),
            child(root, "lastFailedBuild").bind(BuildXml.buildXml),
            child(root, "lastSuccessfulBuild").bind(BuildXml.buildXml),
            child(root, "lastUnsuccessfulBuild").bind(BuildXml.buildXml));
    }
}
