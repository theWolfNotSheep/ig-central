package co.uk.wolfnotsheep.document.models;

/**
 * Types of storage providers that can be connected as drives.
 */
public enum StorageProviderType {
    LOCAL,          // MinIO/local object storage (system default)
    GOOGLE_DRIVE,   // Google Drive via OAuth2
    S3,             // AWS S3 or S3-compatible (e.g. MinIO external)
    SHAREPOINT,     // Microsoft 365 SharePoint / OneDrive
    BOX,            // Box.com
    SMB             // Network shares (SMB/CIFS)
}
