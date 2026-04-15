package co.uk.wolfnotsheep.document.services;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.StorageProviderType;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for storage providers. Each provider (local, Google Drive, S3, etc.)
 * implements this interface to provide unified file browsing, upload, and download.
 *
 * <p>The pipeline always reads from MinIO (the processing cache). Providers are
 * responsible for caching content into MinIO when registering files.
 */
public interface StorageProviderService {

    StorageProviderType getType();

    /** List files in a folder. For LOCAL, uses FolderModel. For external, queries the API. */
    List<FileEntry> listFiles(ConnectedDrive drive, String folderId) throws Exception;

    /** List subfolders of a parent. null parentId = root. */
    List<FileEntry> listFolders(ConnectedDrive drive, String parentId) throws Exception;

    /** Download file content. For external providers, streams from the remote API. */
    InputStream downloadContent(ConnectedDrive drive, String fileId) throws Exception;

    /** Get metadata about a single file. */
    FileEntry getFileInfo(ConnectedDrive drive, String fileId) throws Exception;

    /** Upload a file. For LOCAL, stores in MinIO. For external, uploads to the remote API. */
    void uploadFile(ConnectedDrive drive, String folderId, String fileName,
                    InputStream content, long size, String contentType) throws Exception;

    /** Whether this provider supports OAuth2 connection flow. */
    default boolean supportsOAuth() { return false; }

    /** Get the OAuth2 authorization URL (only for OAuth providers). */
    default String getAuthorizationUrl(ConnectedDrive drive, String state) { return null; }

    /** Provider-agnostic file/folder entry. */
    record FileEntry(
            String id,
            String name,
            String mimeType,
            long size,
            Instant modifiedTime,
            String ownerEmail,
            String webViewLink,
            boolean folder,
            Map<String, String> metadata
    ) {
        public static FileEntry folder(String id, String name) {
            return new FileEntry(id, name, "application/vnd.folder", 0, null, null, null, true, Map.of());
        }
    }
}
