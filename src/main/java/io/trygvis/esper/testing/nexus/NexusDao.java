package io.trygvis.esper.testing.nexus;

import fj.data.*;
import static fj.data.Option.*;
import io.trygvis.esper.testing.*;
import static java.lang.System.currentTimeMillis;

import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.List;

public class NexusDao {
    public static final String NEXUS_SERVER = "uuid, url, name";

    public static final String NEXUS_REPOSITORY = "uuid, server, id, group_ids";

    private final String NEXUS_ARTIFACT_ID = "group_id, artifact_id, version";

    private final String NEXUS_ARTIFACT = "uuid, repository, " + NEXUS_ARTIFACT_ID;

    private final Connection c;

    public NexusDao(Connection c) {
        this.c = c;
    }

    public static URI uri(String s) {
        try {
            return URI.create(s);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return null;
        }
    }

    private static ArtifactDto artifactDto(ResultSet rs) throws SQLException {
        int i = 1;

        return new ArtifactDto(
                UUID.fromString(rs.getString(i++)),
                rs.getString(i++),
                new ArtifactId(rs.getString(i++),
                        rs.getString(i++),
                        rs.getString(i)));
    }

    private NexusRepositoryDto nexusRepositoryDto(ResultSet rs) throws SQLException {
        int i = 1;

        return new NexusRepositoryDto(
                UUID.fromString(rs.getString(i++)),
                UUID.fromString(rs.getString(i++)),
                rs.getString(i++),
                (String[]) rs.getArray(i).getArray()
        );
    }

    // -----------------------------------------------------------------------
    // Nexus Artifact
    // -----------------------------------------------------------------------

    public List<NexusServerDto> selectServer() throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + NEXUS_SERVER + " FROM nexus_server")) {
            ResultSet rs = s.executeQuery();

            List<NexusServerDto> servers = new ArrayList<>();
            while (rs.next()) {
                int i = 1;
                servers.add(new NexusServerDto(
                        UUID.fromString(rs.getString(i++)),
                        uri(rs.getString(i++)),
                        rs.getString(i)));
            }
            return servers;
        }
    }

    public Option<NexusRepositoryDto> findRepository(UUID server, String repositoryId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + NEXUS_REPOSITORY + " FROM nexus_repository WHERE server=? AND id=?")) {
            s.setString(1, server.toString());
            s.setString(2, repositoryId);

            ResultSet rs = s.executeQuery();

            if (!rs.next()) {
                return Option.none();
            }

            return some(nexusRepositoryDto(rs));
        }
    }

    public List<NexusRepositoryDto> findRepositories(URI nexusUrl) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + NEXUS_REPOSITORY + " FROM nexus_repository WHERE server=?")) {
            s.setString(1, nexusUrl.toASCIIString());

            List<NexusRepositoryDto> list = new ArrayList<>();
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    list.add(nexusRepositoryDto(rs));
                }
            }

            return list;
        }
    }

    private int setArtifactId(int i, PreparedStatement s, ArtifactId id) throws SQLException {
        s.setString(i++, id.groupId);
        s.setString(i++, id.artifactId);
        s.setString(i++, id.version);
        return i;
    }

    public UUID insertArtifact(UUID repository, ArtifactId id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO nexus_artifact(" + NEXUS_ARTIFACT + ") VALUES(?, ?, ?, ?, ?)")) {
            UUID uuid = UUID.randomUUID();

            int i = 1;
            s.setString(i++, uuid.toString());
            s.setString(i++, repository.toString());
            setArtifactId(i, s, id);
            s.executeUpdate();

            return uuid;
        }
    }

    public void deleteArtifact(UUID uuid) throws SQLException {
        int i = 1;
        try (PreparedStatement s = c.prepareStatement("DELETE FROM nexus_artifact WHERE uuid=?")) {
            s.setString(i, uuid.toString());
            s.executeUpdate();
        }
    }

    public List<ArtifactDto> findArtifactsInRepository(URI url, String repositoryId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + NEXUS_ARTIFACT + " FROM nexus_artifact WHERE server_url=? AND repository_id=?")) {
            s.setString(1, url.toASCIIString());
            s.setString(2, repositoryId);
            ResultSet rs = s.executeQuery();

            List<ArtifactDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(artifactDto(rs));
            }
            return list;
        }
    }

    public Option<ArtifactDto> findArtifact(UUID repository, ArtifactId id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + NEXUS_ARTIFACT + " FROM nexus_artifact WHERE repository=? AND group_id=? AND artifact_id=? AND version =?")) {
            int i = 1;
            s.setString(i++, repository.toString());
            setArtifactId(i, s, id);

            ResultSet rs = s.executeQuery();
            if (!rs.next()) {
                return none();
            }

            return some(artifactDto(rs));
        }
    }

//    public void updateSnapshotTimestamp(UUID uuid, String snapshotTimestamp) throws SQLException {
//        try(PreparedStatement s = c.prepareStatement("UPDATE nexus_artifact SET snapshot_timestamp=? WHERE uuid=?")) {
//            s.setString(1, snapshotTimestamp);
//            s.setString(2, uuid.toString());
//            s.executeUpdate();
//        }
//    }

    public UUID insertNewSnapshotEvent(UUID artifact, String guid, String snapshotTimestamp, int buildNumber, String file) throws SQLException {
        try(PreparedStatement s = c.prepareStatement("INSERT INTO nexus_event(uuid, artifact, timestamp, guid, type, snapshot_timestamp, build_number, file) VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
            UUID uuid = UUID.randomUUID();
            int i = 1;
            s.setString(i++, uuid.toString());
            s.setString(i++, artifact.toString());
            s.setTimestamp(i++, new Timestamp(currentTimeMillis()));
            s.setString(i++, guid);
            s.setString(i++, "new_snapshot");
            s.setString(i++, snapshotTimestamp);
            s.setInt(i++, buildNumber);
            s.setString(i, file);
            s.executeUpdate();
            return uuid;
        }
    }

    public UUID insertNewReleaseEvent(UUID artifact, String guid, String file) throws SQLException {
        try(PreparedStatement s = c.prepareStatement("INSERT INTO nexus_event(uuid, artifact, timestamp, guid, type, file) VALUES(?, ?, ?, ?, ?, ?)")) {
            UUID uuid = UUID.randomUUID();
            int i = 1;
            s.setString(i++, uuid.toString());
            s.setString(i++, artifact.toString());
            s.setTimestamp(i++, new Timestamp(currentTimeMillis()));
            s.setString(i++, guid);
            s.setString(i++, "new_release");
            s.setString(i, file);
            s.executeUpdate();
            return uuid;
        }
    }

    public int countEventByGuid(String guid) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT count(guid) FROM nexus_event WHERE guid=?")) {
            s.setString(1, guid);
            ResultSet rs = s.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }
}

class NexusServerDto {
    public final UUID uuid;

    public final URI url;

    public final String name;

    NexusServerDto(UUID uuid, URI url, String name) {
        this.uuid = uuid;
        this.url = url;
        this.name = name;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NexusServerDto that = (NexusServerDto) o;

        return uuid.equals(that.uuid);
    }

    public int hashCode() {
        return uuid.hashCode();
    }
}

class NexusRepositoryDto {
    public final UUID uuid;

    public final UUID server;

    public final String repositoryId;

    public final String[] groupIds;

    NexusRepositoryDto(UUID uuid, UUID server, String repositoryId, String[] groupIds) {
        this.uuid = uuid;
        this.server = server;
        this.repositoryId = repositoryId;
        this.groupIds = groupIds;
    }
}
