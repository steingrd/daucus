package io.trygvis.esper.testing.jenkins;

import fj.*;
import fj.data.*;
import io.trygvis.esper.testing.core.db.*;
import io.trygvis.esper.testing.jenkins.xml.*;
import io.trygvis.esper.testing.util.object.*;
import io.trygvis.esper.testing.util.sql.*;
import org.slf4j.*;

import java.net.*;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.*;
import java.util.Set;

import static io.trygvis.esper.testing.jenkins.JenkinsClient.*;
import static java.lang.System.*;

public class JenkinsServerActor implements TransactionalActor {
    private static final Logger logger = LoggerFactory.getLogger(JenkinsServerActor.class);
    private final JenkinsClient client;
    public final JenkinsServerDto server;

    public JenkinsServerActor(JenkinsClient client, JenkinsServerDto server) {
        this.client = client;
        this.server = server;
    }

    public void act(Connection c) throws Exception {
        long start = currentTimeMillis();

        JenkinsDao dao = new JenkinsDao(c);
        FileDao fileDao = new FileDao(c);

        URI rssUrl = URI.create(server.url.toASCIIString() + "/rssAll");
        Option<P2<List<JenkinsEntryXml>, byte[]>> option = client.fetchRss(rssUrl);

        if (option.isNone()) {
            return;
        }

        fileDao.store(rssUrl, "application/xml", option.some()._2());

        List<JenkinsEntryXml> list = option.some()._1();

        logger.info("Got " + list.size() + " entries.");

        int i = 0;

        Map<String, UUID> authors = new HashMap<>();

        for (JenkinsEntryXml entry : list) {
            SqlOption<JenkinsBuildDto> o = dao.selectBuildByEntryId(entry.id);

            if (o.isSome()) {
//                logger.debug("Old build: " + entry.id);
                continue;
            }

            logger.debug("Build: " + entry.id + ", fetching info");

            URI buildUrl = apiXml(entry.url);

            Option<P2<JenkinsBuildXml, byte[]>> buildXmlOption = client.fetchBuild(buildUrl);

            if (buildXmlOption.isNone()) {
                continue;
            }

            JenkinsBuildXml build = buildXmlOption.some()._1();

            if (build.result.isNone()) {
                logger.debug("Not done building, <result> is not available.");
                continue;
            }

            UUID buildXmlFile = fileDao.store(buildUrl, "application/xml", buildXmlOption.some()._2());

            // -----------------------------------------------------------------------
            // Users
            // -----------------------------------------------------------------------

            Set<UUID> users = new HashSet<>();

            if (build.changeSet.isSome()) {
                JenkinsBuildXml.ChangeSetXml changeSetXml = build.changeSet.some();

                for (JenkinsBuildXml.ChangeSetItemXml item : changeSetXml.items) {
                    if (item.author.isNone()) {
                        continue;
                    }

                    String url = item.author.some().absoluteUrl;

                    UUID uuid = authors.get(url);

                    if (uuid == null) {
                        SqlOption<JenkinsUserDto> userO = dao.selectUserByAbsoluteUrl(server.uuid, url);
                        if (userO.isNone()) {
                            logger.info("New user: {}", url);
                            uuid = dao.insertUser(server.uuid, url);
                        } else {
                            uuid = userO.get().uuid;
                        }

                        authors.put(url, uuid);
                    }

                    users.add(uuid);
                }
            }

            // -----------------------------------------------------------------------
            // Job
            // -----------------------------------------------------------------------

            URI jobUrl = extrapolateJobUrlFromBuildUrl(build.url.toASCIIString());

            SqlOption<JenkinsJobDto> jobDtoOption = dao.selectJobByUrl(jobUrl);

            UUID job;

            if (jobDtoOption.isSome()) {
                job = jobDtoOption.get().uuid;
            } else {
                logger.info("New job: {}, fetching info", jobUrl);

                URI uri = apiXml(jobUrl);

                Option<P2<JenkinsJobXml, byte[]>> jobXmlOption = client.fetchJob(uri);

                if (jobXmlOption.isNone()) {
                    continue;
                }

                UUID jobXmlFile = fileDao.store(uri, "application/xml", jobXmlOption.some()._2());

                JenkinsJobXml xml = jobXmlOption.some()._1();

                job = dao.insertJob(server.uuid, jobXmlFile, xml.url, xml.type, xml.displayName);

                logger.info("New job: {}, uuid={}", xml.displayName.orSome(xml.url.toASCIIString()), job);
            }

            i++;

            UUID uuid = dao.insertBuild(
                    job,
                    buildXmlFile,
                    entry.id,
                    build.url,
                    users.toArray(new UUID[users.size()]));

            logger.info("Build inserted: {}, #users={} item #{}/{}", uuid, users.size(), i, list.size());
        }

        long end = currentTimeMillis();

        logger.info("Inserted " + i + " of " + list.size() + " builds in " + (end - start) + "ms.");
    }

    /**
     * This sucks, a build should really include the URL to the job.
     */
    public static URI extrapolateJobUrlFromBuildUrl(String u) {
        if (!u.matches(".*/[0-9]*/")) {
            throw new RuntimeException("Not a valid build url: " + u);
        }

        u = u.substring(0, u.lastIndexOf("/"));
        u = u.substring(0, u.lastIndexOf("/") + 1);

        return URI.create(u);
    }

    public static String extrapolateMavenModuleFromMavenModuleSetUrl(String u) {
        int i = u.lastIndexOf("/");
        if (i == -1) {
            throw new RuntimeException("Illegal URL");
        }
        u = u.substring(0, i);
        i = u.lastIndexOf("/");
        if (i == -1) {
            throw new RuntimeException("Illegal URL");
        }
        return u.substring(0, i + 1);
    }
}
