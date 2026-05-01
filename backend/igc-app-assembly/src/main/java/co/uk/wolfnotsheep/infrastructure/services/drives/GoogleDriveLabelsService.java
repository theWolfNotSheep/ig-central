package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.ModifyLabelsRequest;
import com.google.api.services.drive.model.LabelFieldModification;
import com.google.api.services.drive.model.LabelModification;
import com.google.api.services.drivelabels.v2.DriveLabels;
import com.google.api.services.drivelabels.v2.model.GoogleAppsDriveLabelsV2Label;
import com.google.api.services.drivelabels.v2.model.GoogleAppsDriveLabelsV2Field;
import com.google.api.services.drivelabels.v2.model.GoogleAppsDriveLabelsV2ListLabelsResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;

/**
 * Google Drive Labels API wrapper. Lists available Workspace labels
 * and applies label instances to files via the Drive API.
 *
 * Listing labels uses the DriveLabels API (google-api-services-drivelabels).
 * Applying labels to files uses the Drive API's files().modifyLabels().
 */
@Service
public class GoogleDriveLabelsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveLabelsService.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GoogleDriveService googleDriveService;

    public GoogleDriveLabelsService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    // ── Records ──────────────────────────────────────────────

    public record LabelInfo(String id, String name, List<LabelFieldInfo> fields) {}
    public record LabelFieldInfo(String id, String displayName, String type) {}

    // ── List available labels ────────────────────────────────

    /**
     * List all published labels available in the user's Workspace.
     * Requires the drive.labels scope.
     */
    public List<LabelInfo> listAvailableLabels(ConnectedDrive drive) throws Exception {
        DriveLabels labelsService = buildLabelsService(drive);

        GoogleAppsDriveLabelsV2ListLabelsResponse response = labelsService.labels()
                .list()
                .setPublishedOnly(true)
                .setPageSize(50)
                .execute();

        if (response.getLabels() == null) {
            return List.of();
        }

        List<LabelInfo> result = new ArrayList<>();
        for (GoogleAppsDriveLabelsV2Label label : response.getLabels()) {
            List<LabelFieldInfo> fields = new ArrayList<>();
            if (label.getFields() != null) {
                for (GoogleAppsDriveLabelsV2Field field : label.getFields()) {
                    String fieldType = "TEXT";
                    if (field.getSelectionOptions() != null) fieldType = "SELECTION";
                    else if (field.getIntegerOptions() != null) fieldType = "INTEGER";
                    else if (field.getDateOptions() != null) fieldType = "DATE";
                    else if (field.getUserOptions() != null) fieldType = "USER";
                    else if (field.getTextOptions() != null) fieldType = "TEXT";

                    String displayName = field.getProperties() != null
                            ? field.getProperties().getDisplayName() : field.getId();

                    fields.add(new LabelFieldInfo(field.getId(), displayName, fieldType));
                }
            }

            String labelName = label.getProperties() != null
                    ? label.getProperties().getTitle() : label.getId();

            result.add(new LabelInfo(label.getId(), labelName, fields));
        }

        log.debug("Found {} labels for drive {}", result.size(), drive.getProviderAccountEmail());
        return result;
    }

    // ── Apply label to a file ────────────────────────────────

    /**
     * Write classification metadata to a Google Drive file as a native Drive Label.
     * Uses the Drive API's files().modifyLabels() method (not the Labels API).
     */
    public void writeClassificationLabel(ConnectedDrive drive, String fileId,
                                          String labelId, Map<String, String> fieldMappings,
                                          DocumentModel doc) throws Exception {
        if (fieldMappings == null || fieldMappings.isEmpty()) {
            log.debug("No field mappings configured for label {}, skipping", labelId);
            return;
        }

        Drive driveService = buildDriveService(drive);

        List<LabelFieldModification> fieldMods = new ArrayList<>();

        // Map each IGC classification field to the configured label field ID
        addFieldModification(fieldMods, fieldMappings, "category",
                doc.getCategoryName());
        addFieldModification(fieldMods, fieldMappings, "sensitivity",
                doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : null);
        addFieldModification(fieldMods, fieldMappings, "retention_until",
                doc.getRetentionExpiresAt() != null ? doc.getRetentionExpiresAt().toString() : null);
        addFieldModification(fieldMods, fieldMappings, "vital_record",
                doc.isVitalRecord() ? "Yes" : "No");
        addFieldModification(fieldMods, fieldMappings, "legal_hold",
                doc.isLegalHold() ? "Yes" : "No");

        if (fieldMods.isEmpty()) {
            log.debug("No field modifications to apply for label {} on file {}", labelId, fileId);
            return;
        }

        LabelModification labelMod = new LabelModification()
                .setLabelId(labelId)
                .setFieldModifications(fieldMods);

        ModifyLabelsRequest request = new ModifyLabelsRequest()
                .setLabelModifications(List.of(labelMod));

        driveService.files().modifyLabels(fileId, request).execute();

        log.info("Applied Drive Label {} with {} fields to file {}", labelId, fieldMods.size(), fileId);
    }

    // ── Helpers ──────────────────────────────────────────────

    private void addFieldModification(List<LabelFieldModification> mods,
                                       Map<String, String> fieldMappings,
                                       String igcKey, String value) {
        String fieldId = fieldMappings.get(igcKey);
        if (fieldId == null || fieldId.isBlank() || value == null || value.isBlank()) return;

        mods.add(new LabelFieldModification()
                .setFieldId(fieldId)
                .setSetTextValues(List.of(value)));
    }

    private DriveLabels buildLabelsService(ConnectedDrive drive) throws Exception {
        ConnectedDrive refreshed = googleDriveService.refreshTokenIfNeeded(drive);
        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredentials credentials = buildCredentials(refreshed);

        return new DriveLabels.Builder(transport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName("IG Central")
                .build();
    }

    private Drive buildDriveService(ConnectedDrive drive) throws GeneralSecurityException, Exception {
        ConnectedDrive refreshed = googleDriveService.refreshTokenIfNeeded(drive);
        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredentials credentials = buildCredentials(refreshed);

        return new Drive.Builder(transport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName("IG Central")
                .build();
    }

    private GoogleCredentials buildCredentials(ConnectedDrive drive) {
        if (drive.getRefreshToken() != null && !drive.getRefreshToken().isBlank()) {
            return com.google.auth.oauth2.UserCredentials.newBuilder()
                    .setClientId(googleDriveService.getClientId())
                    .setClientSecret(googleDriveService.getClientSecret())
                    .setRefreshToken(drive.getRefreshToken())
                    .setAccessToken(new AccessToken(drive.getAccessToken(),
                            Date.from(drive.getTokenExpiresAt())))
                    .build();
        }
        return GoogleCredentials.create(
                new AccessToken(drive.getAccessToken(), Date.from(drive.getTokenExpiresAt())));
    }
}
