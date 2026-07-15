package ru.arzer0.issueisekai.panel.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResourcePackService {
    private static final long MAX_PACK_SIZE = 100L * 1024 * 1024;
    private static final long MAX_ENTRY_SIZE = 16L * 1024 * 1024;
    private static final long MAX_EXPANDED_SIZE = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 50_000;
    private static final int MAX_RATIO = 200;
    private static final int MINECRAFT_26_1_PACK_FORMAT = 75;
    private final ObjectProvider<NamedParameterJdbcTemplate> databases;
    private final ObjectMapper json;
    private final Path directory;

    public ResourcePackService(
            ObjectProvider<NamedParameterJdbcTemplate> databases,
            ObjectMapper json,
            @Value("${resource-packs.directory}") String directory) {
        this.databases = databases;
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<Revision> list(UUID serverId) {
        requireServer(serverId);
        return database().query(
                """
                        SELECT rp.*, s.active_resource_pack_id = rp.id AS active
                        FROM resource_packs rp
                        JOIN servers s ON s.id = rp.server_id
                        WHERE rp.server_id = :serverId AND rp.kind = 'SERVER'
                        ORDER BY rp.created_at DESC, rp.id DESC
                        """,
                new MapSqlParameterSource("serverId", serverId),
                ResourcePackService::revision);
    }

    @Transactional
    public Revision upload(
            UUID serverId,
            String displayName,
            String minecraftVersion,
            UUID resourcePackId,
            MultipartFile file) {
        requireServer(serverId);
        String name = normalize(displayName, 100, "Display name");
        String version = normalize(minecraftVersion, 64, "Minecraft version");
        if (resourcePackId == null) {
            throw new IllegalArgumentException("Resource pack UUID is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resource pack ZIP is required");
        }
        Path temporary = temporaryFile(".upload-");
        try {
            Hashes hashes = copyAndHash(file, temporary);
            PackFormat format = validateZip(temporary);
            validateCompatibility(version, format);
            Revision existing = findServerRevision(serverId, hashes.sha256());
            if (existing != null) {
                return existing;
            }
            store(temporary, hashes.sha256Hex());
            UUID id = UUID.randomUUID();
            OffsetDateTime createdAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            int inserted = database().update(
                    """
                            INSERT INTO resource_packs (
                                id, kind, server_id, display_name, minecraft_version,
                                pack_format_min, pack_format_max, resource_pack_id,
                                sha1, content_sha256, size_bytes, created_at
                            ) VALUES (
                                :id, 'SERVER', :serverId, :displayName, :minecraftVersion,
                                :formatMin, :formatMax, :resourcePackId,
                                :sha1, :sha256, :size, :createdAt
                            )
                            ON CONFLICT DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("serverId", serverId)
                            .addValue("displayName", name)
                            .addValue("minecraftVersion", version)
                            .addValue("formatMin", format.minimum())
                            .addValue("formatMax", format.maximum())
                            .addValue("resourcePackId", resourcePackId)
                            .addValue("sha1", hashes.sha1())
                            .addValue("sha256", hashes.sha256())
                            .addValue("size", hashes.size())
                            .addValue("createdAt", createdAt));
            if (inserted == 0) {
                Revision duplicate = findServerRevision(serverId, hashes.sha256());
                if (duplicate != null) {
                    return duplicate;
                }
                throw new IllegalArgumentException("Resource pack revision conflicts with existing data");
            }
            return new Revision(
                    id,
                    serverId,
                    "SERVER",
                    name,
                    version,
                    format.minimum(),
                    format.maximum(),
                    resourcePackId,
                    hashes.sha1Hex(),
                    hashes.sha256Hex(),
                    hashes.size(),
                    false,
                    createdAt.toInstant());
        } finally {
            delete(temporary);
        }
    }

    @Transactional
    public void activate(UUID serverId, UUID revisionId) {
        int updated = database().update(
                """
                        UPDATE servers s
                        SET active_resource_pack_id = :revisionId
                        WHERE s.id = :serverId
                          AND EXISTS (
                              SELECT 1
                              FROM resource_packs rp
                              WHERE rp.id = :revisionId
                                AND rp.server_id = s.id
                                AND rp.kind = 'SERVER'
                          )
                        """,
                new MapSqlParameterSource()
                        .addValue("serverId", serverId)
                        .addValue("revisionId", revisionId));
        if (updated == 0) {
            requireServer(serverId);
            throw new RevisionNotFoundException();
        }
    }

    @Transactional
    public Revision importVanilla(Path clientJar, String minecraftVersion) {
        String version = normalize(minecraftVersion, 64, "Minecraft version");
        if (clientJar == null || !Files.isRegularFile(clientJar)) {
            throw new IllegalArgumentException("Client JAR not found");
        }
        List<Revision> existing = database().query(
                "SELECT rp.*, false AS active FROM resource_packs rp WHERE kind = 'VANILLA_BASE' AND minecraft_version = :version",
                new MapSqlParameterSource("version", version),
                ResourcePackService::revision);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        Path temporary = temporaryFile(".vanilla-");
        try {
            buildVanillaBundle(clientJar, version, temporary);
            Hashes hashes = hash(temporary);
            PackFormat format = validateZip(temporary);
            validateCompatibility(version, format);
            store(temporary, hashes.sha256Hex());
            UUID id = UUID.randomUUID();
            OffsetDateTime createdAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            int inserted = database().update(
                    """
                            INSERT INTO resource_packs (
                                id, kind, display_name, minecraft_version,
                                pack_format_min, pack_format_max,
                                content_sha256, size_bytes, created_at
                            ) VALUES (
                                :id, 'VANILLA_BASE', :displayName, :minecraftVersion,
                                :formatMin, :formatMax, :sha256, :size, :createdAt
                            )
                            ON CONFLICT DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("displayName", "Vanilla " + version)
                            .addValue("minecraftVersion", version)
                            .addValue("formatMin", format.minimum())
                            .addValue("formatMax", format.maximum())
                            .addValue("sha256", hashes.sha256())
                            .addValue("size", hashes.size())
                            .addValue("createdAt", createdAt));
            if (inserted == 0) {
                return database()
                        .query(
                                "SELECT rp.*, false AS active FROM resource_packs rp WHERE kind = 'VANILLA_BASE' AND minecraft_version = :version",
                                new MapSqlParameterSource("version", version),
                                ResourcePackService::revision)
                        .getFirst();
            }
            return new Revision(
                    id,
                    null,
                    "VANILLA_BASE",
                    "Vanilla " + version,
                    version,
                    format.minimum(),
                    format.maximum(),
                    null,
                    null,
                    hashes.sha256Hex(),
                    hashes.size(),
                    false,
                    createdAt.toInstant());
        } finally {
            delete(temporary);
        }
    }

    private void buildVanillaBundle(Path clientJar, String version, Path output) {
        try (ZipFile source = new ZipFile(clientJar.toFile())) {
            ZipEntry versionEntry = source.getEntry("version.json");
            if (versionEntry == null) {
                throw new IllegalArgumentException("Client JAR has no version.json");
            }
            JsonNode versionJson = json.readTree(read(source, versionEntry, 64 * 1024));
            if (!version.equals(versionJson.path("id").asText())) {
                throw new IllegalArgumentException("Client JAR Minecraft version does not match");
            }
            List<? extends ZipEntry> entries = source.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().equals("pack.mcmeta")
                            || entry.getName().startsWith("assets/minecraft/"))
                    .sorted((left, right) -> left.getName().compareTo(right.getName()))
                    .toList();
            try (ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
                for (ZipEntry entry : entries) {
                    validateName(entry.getName());
                    ZipEntry copy = new ZipEntry(entry.getName());
                    copy.setTime(0);
                    target.putNextEntry(copy);
                    try (InputStream input = source.getInputStream(entry)) {
                        copy(input, target, MAX_ENTRY_SIZE);
                    }
                    target.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot import vanilla assets", exception);
        }
    }

    private Hashes copyAndHash(MultipartFile file, Path target) {
        if (file.getSize() < 1 || file.getSize() > MAX_PACK_SIZE) {
            throw new IllegalArgumentException("Resource pack ZIP must be between 1 byte and 100 MiB");
        }
        MessageDigest sha1 = digest("SHA-1");
        MessageDigest sha256 = digest("SHA-256");
        long size = 0;
        try (InputStream input = file.getInputStream();
                OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read == 0) {
                    continue;
                }
                size += read;
                if (size > MAX_PACK_SIZE) {
                    throw new IllegalArgumentException("Resource pack ZIP exceeds 100 MiB");
                }
                sha1.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot store resource pack ZIP", exception);
        }
        byte[] sha1Bytes = sha1.digest();
        byte[] sha256Bytes = sha256.digest();
        return new Hashes(
                sha1Bytes,
                sha256Bytes,
                HexFormat.of().formatHex(sha1Bytes),
                HexFormat.of().formatHex(sha256Bytes),
                size);
    }

    private Hashes hash(Path file) {
        MessageDigest sha1 = digest("SHA-1");
        MessageDigest sha256 = digest("SHA-256");
        long size = 0;
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read == 0) {
                    continue;
                }
                size += read;
                if (size > MAX_PACK_SIZE) {
                    throw new IllegalArgumentException("Resource pack ZIP exceeds 100 MiB");
                }
                sha1.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot hash resource pack ZIP", exception);
        }
        byte[] sha1Bytes = sha1.digest();
        byte[] sha256Bytes = sha256.digest();
        return new Hashes(
                sha1Bytes,
                sha256Bytes,
                HexFormat.of().formatHex(sha1Bytes),
                HexFormat.of().formatHex(sha256Bytes),
                size);
    }

    private PackFormat validateZip(Path file) {
        Set<String> names = new HashSet<>();
        byte[] metadata = null;
        long expanded = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                validateName(entry.getName());
                if (!names.add(entry.getName())) {
                    throw new IllegalArgumentException("Resource pack ZIP contains duplicate entries");
                }
                if (++count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Resource pack ZIP has too many entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] content = read(zip, entry, MAX_ENTRY_SIZE);
                expanded += content.length;
                if (expanded > MAX_EXPANDED_SIZE
                        || expanded > Math.max(1, Files.size(file)) * MAX_RATIO) {
                    throw new IllegalArgumentException("Resource pack ZIP expands beyond safe limits");
                }
                if (entry.getName().equals("pack.mcmeta")) {
                    metadata = content;
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid resource pack ZIP", exception);
        }
        if (metadata == null) {
            throw new IllegalArgumentException("Resource pack ZIP has no pack.mcmeta");
        }
        try {
            JsonNode pack = json.readTree(metadata).path("pack");
            int current = pack.path("pack_format").asInt(0);
            int minimum = current;
            int maximum = current;
            JsonNode supported = pack.path("supported_formats");
            if (supported.isArray() && supported.size() == 2) {
                minimum = supported.get(0).asInt(0);
                maximum = supported.get(1).asInt(0);
            } else if (supported.isObject()) {
                minimum = supported.path("min_inclusive").asInt(current);
                maximum = supported.path("max_inclusive").asInt(current);
            }
            if (minimum < 1 || maximum < minimum) {
                throw new IllegalArgumentException("pack.mcmeta contains invalid pack formats");
            }
            return new PackFormat(minimum, maximum);
        } catch (IOException exception) {
            throw new IllegalArgumentException("pack.mcmeta is not valid JSON", exception);
        }
    }

    private static byte[] read(ZipFile zip, ZipEntry entry, long maximum) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            copy(input, output, maximum);
            return output.toByteArray();
        }
    }

    private static long copy(InputStream input, OutputStream output, long maximum) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int read; (read = input.read(buffer)) >= 0; ) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maximum) {
                throw new IllegalArgumentException("Resource pack ZIP entry is too large");
            }
            output.write(buffer, 0, read);
        }
        return total;
    }

    private static void validateName(String name) {
        if (!StringUtils.hasText(name)
                || name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("\\")
                || name.indexOf('\0') >= 0
                || name.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Resource pack ZIP contains an unsafe path");
        }
        String[] segments = name.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].equals("..")
                    || segments[index].equals(".")
                    || (segments[index].isEmpty() && index != segments.length - 1)) {
                throw new IllegalArgumentException("Resource pack ZIP contains an unsafe path");
            }
        }
    }

    private Revision findServerRevision(UUID serverId, byte[] sha256) {
        List<Revision> revisions = database().query(
                """
                        SELECT rp.*, s.active_resource_pack_id = rp.id AS active
                        FROM resource_packs rp
                        JOIN servers s ON s.id = rp.server_id
                        WHERE rp.server_id = :serverId
                          AND rp.kind = 'SERVER'
                          AND rp.content_sha256 = :sha256
                        """,
                new MapSqlParameterSource()
                        .addValue("serverId", serverId)
                        .addValue("sha256", sha256),
                ResourcePackService::revision);
        return revisions.isEmpty() ? null : revisions.getFirst();
    }

    private void requireServer(UUID serverId) {
        Boolean exists = database().queryForObject(
                "SELECT EXISTS (SELECT 1 FROM servers WHERE id = :id)",
                new MapSqlParameterSource("id", serverId),
                Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ServerService.ServerNotFoundException();
        }
    }

    private Path temporaryFile(String prefix) {
        try {
            Files.createDirectories(directory);
            return Files.createTempFile(directory, prefix, ".zip");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create resource pack directory", exception);
        }
    }

    private void store(Path temporary, String sha256) {
        Path target = directory.resolve(sha256 + ".zip");
        if (Files.exists(target)) {
            return;
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ignored) {
            // Another upload stored the same immutable content first.
        } catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(temporary, target);
            } catch (FileAlreadyExistsException ignored) {
                // Another upload stored the same immutable content first.
            } catch (IOException moveException) {
                throw new IllegalStateException("Cannot store resource pack ZIP", moveException);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot store resource pack ZIP", exception);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort cleanup of a temporary upload.
        }
    }

    private static String normalize(String value, int maximum, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is required and must be at most " + maximum + " characters");
        }
        return normalized;
    }

    private static void validateCompatibility(String minecraftVersion, PackFormat format) {
        if ((minecraftVersion.equals("26.1") || minecraftVersion.startsWith("26.1."))
                && (format.minimum() > MINECRAFT_26_1_PACK_FORMAT
                        || format.maximum() < MINECRAFT_26_1_PACK_FORMAT)) {
            throw new IllegalArgumentException("Resource pack is not compatible with Minecraft 26.1");
        }
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private NamedParameterJdbcTemplate database() {
        return databases.getObject();
    }

    private static Revision revision(ResultSet resultSet, int row) throws SQLException {
        byte[] sha1 = resultSet.getBytes("sha1");
        return new Revision(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("server_id", UUID.class),
                resultSet.getString("kind"),
                resultSet.getString("display_name"),
                resultSet.getString("minecraft_version"),
                resultSet.getInt("pack_format_min"),
                resultSet.getInt("pack_format_max"),
                resultSet.getObject("resource_pack_id", UUID.class),
                sha1 == null ? null : HexFormat.of().formatHex(sha1),
                HexFormat.of().formatHex(resultSet.getBytes("content_sha256")),
                resultSet.getLong("size_bytes"),
                resultSet.getBoolean("active"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    public record Revision(
            UUID id,
            UUID serverId,
            String kind,
            String displayName,
            String minecraftVersion,
            int packFormatMin,
            int packFormatMax,
            UUID resourcePackId,
            String sha1,
            String sha256,
            long sizeBytes,
            boolean active,
            Instant createdAt) {}

    public static final class RevisionNotFoundException extends IllegalArgumentException {
        public RevisionNotFoundException() {
            super("Resource pack revision not found for server");
        }
    }

    private record Hashes(
            byte[] sha1,
            byte[] sha256,
            String sha1Hex,
            String sha256Hex,
            long size) {}

    private record PackFormat(int minimum, int maximum) {}
}
