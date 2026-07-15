package ru.arzer0.issueisekai.panel.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SuppressWarnings({"unchecked", "rawtypes"})
class ResourcePackServiceTest {
    @TempDir Path directory;

    @Test
    void validatesStoresAndDeduplicatesPackByHash() throws Exception {
        NamedParameterJdbcTemplate database = mock(NamedParameterJdbcTemplate.class);
        ObjectProvider<NamedParameterJdbcTemplate> databases = mock(ObjectProvider.class);
        when(databases.getObject()).thenReturn(database);
        when(database.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        var stored = new AtomicReference<ResourcePackService.Revision>();
        when(database.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    return sql.contains("content_sha256") && stored.get() != null
                            ? List.of(stored.get())
                            : List.of();
                });
        when(database.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        var service = new ResourcePackService(databases, new ObjectMapper(), directory.toString());
        UUID serverId = UUID.randomUUID();
        byte[] zip = zip("assets/example/items/ruby.json", "{}");
        var upload = new MockMultipartFile("file", "pack.zip", "application/zip", zip);

        ResourcePackService.Revision first =
                service.upload(serverId, "Lobby pack", "26.1.2", upload);
        stored.set(first);
        ResourcePackService.Revision duplicate =
                service.upload(serverId, "Renamed", "26.1.2", upload);

        assertEquals(first, duplicate);
        assertEquals(40, first.sha1().length());
        assertEquals(64, first.sha256().length());
        assertTrue(Files.exists(directory.resolve(first.sha256() + ".zip")));
        assertEquals(1, Files.list(directory).filter(Files::isRegularFile).count());
        verify(database, times(1)).update(anyString(), any(MapSqlParameterSource.class));

        var unsafe = new MockMultipartFile(
                "file", "unsafe.zip", "application/zip", zip("../escape.json", "{}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(serverId, "Unsafe", "26.1.2", unsafe));
        assertFalse(Files.exists(directory.resolve("escape.json")));

        var incompatible = new MockMultipartFile(
                "file",
                "old.zip",
                "application/zip",
                zip("assets/example/items/ruby.json", "{}", 74));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(serverId, "Old format", "26.1.2", incompatible));
    }

    @Test
    void readsOnlyAllowlistedAssetsFromImmutableZip() throws Exception {
        byte[] zip = zip("assets/example/items/ruby.json", "{}");
        String sha256 = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
        Files.write(directory.resolve(sha256 + ".zip"), zip);
        NamedParameterJdbcTemplate database = mock(NamedParameterJdbcTemplate.class);
        ObjectProvider<NamedParameterJdbcTemplate> databases = mock(ObjectProvider.class);
        when(databases.getObject()).thenReturn(database);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("kind")).thenReturn("SERVER");
        when(resultSet.getString("minecraft_version")).thenReturn("26.1.2");
        when(resultSet.getString("sha256")).thenReturn(sha256);
        when(database.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        var service = new ResourcePackService(databases, new ObjectMapper(), directory.toString());

        ResourcePackService.Asset asset = service.asset(
                UUID.randomUUID(), "assets/example/items/ruby.json");

        assertEquals("application/json", asset.contentType());
        assertEquals("{}", new String(asset.content(), StandardCharsets.UTF_8));
        assertThrows(
                ResourcePackService.AssetNotFoundException.class,
                () -> service.asset(UUID.randomUUID(), "assets/example/sounds/unsafe.ogg"));

        byte[] invalidPng = zip("assets/example/textures/fake.png", "not-a-png");
        String invalidSha = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(invalidPng));
        Files.write(directory.resolve(invalidSha + ".zip"), invalidPng);
        when(resultSet.getString("sha256")).thenReturn(invalidSha);
        assertThrows(
                ResourcePackService.AssetNotFoundException.class,
                () -> service.asset(
                        UUID.randomUUID(), "assets/example/textures/fake.png"));
    }

    @Test
    void rejectsInvalidUnsafeAndCrossServerPacks() throws Exception {
        NamedParameterJdbcTemplate database = mock(NamedParameterJdbcTemplate.class);
        ObjectProvider<NamedParameterJdbcTemplate> databases = mock(ObjectProvider.class);
        when(databases.getObject()).thenReturn(database);
        when(database.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(database.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        var service = new ResourcePackService(databases, new ObjectMapper(), directory.toString());
        UUID serverId = UUID.randomUUID();
        assertRejected(service, serverId, file("text/plain", zip("assets/example/item.json", "{}")));
        assertRejected(service, serverId, file("application/zip", rawZip("pack.mcmeta", "not-json")));
        assertRejected(service, serverId, file("application/zip", zip("/absolute.json", "{}")));
        assertRejected(service, serverId, file("application/zip", duplicateEntryZip()));
        assertRejected(service, serverId, file("application/zip", zip("assets/example/bomb.json", "0".repeat(1_048_576))));
        assertThrows(
                ResourcePackService.RevisionNotFoundException.class,
                () -> service.activate(serverId, UUID.randomUUID()));
    }

    private static byte[] zip(String assetName, String asset) throws IOException {
        return zip(assetName, asset, 75);
    }

    private static byte[] zip(String assetName, String asset, int packFormat) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(
                    zip,
                    "pack.mcmeta",
                    """
                    {"pack":{"pack_format":%d,"supported_formats":[%d,%d],"description":"test"}}
                    """
                            .formatted(packFormat, packFormat, packFormat));
            entry(zip, assetName, asset);
        }
        return bytes.toByteArray();
    }

    private static byte[] rawZip(String name, String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, name, value);
        }
        return bytes.toByteArray();
    }

    private static byte[] duplicateEntryZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "pack.mcmeta", "{\"pack\":{\"pack_format\":75}}");
            entry(zip, "assets/example/a.json", "{}");
            entry(zip, "assets/example/b.json", "{}");
        }
        byte[] result = bytes.toByteArray();
        byte[] from = "assets/example/b.json".getBytes(StandardCharsets.UTF_8);
        byte[] to = "assets/example/a.json".getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index <= result.length - from.length; index++) {
            boolean match = true;
            for (int offset = 0; offset < from.length; offset++) {
                match &= result[index + offset] == from[offset];
            }
            if (match) {
                System.arraycopy(to, 0, result, index, to.length);
            }
        }
        return result;
    }

    private static MockMultipartFile file(String contentType, byte[] content) {
        return new MockMultipartFile("file", "pack.zip", contentType, content);
    }

    private static void assertRejected(
            ResourcePackService service,
            UUID serverId,
            MockMultipartFile file) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(serverId, "Invalid", "26.1.2", file));
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
