package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the last poll position for a Gmail watcher pipeline node.
 * One cursor per connected account + query combination.
 */
@Document(collection = "gmail_ingest_cursors")
public class GmailIngestCursor {

    @Id
    private String id;

    @Indexed
    private String connectedDriveId;  // the Gmail ConnectedDrive

    private String query;              // the Gmail search query
    private String lastHistoryId;      // Gmail history ID for incremental sync
    private Instant lastPollAt;
    private int messagesIngested;

    public GmailIngestCursor() {}

    // ── Getters & setters ─────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConnectedDriveId() { return connectedDriveId; }
    public void setConnectedDriveId(String connectedDriveId) { this.connectedDriveId = connectedDriveId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getLastHistoryId() { return lastHistoryId; }
    public void setLastHistoryId(String lastHistoryId) { this.lastHistoryId = lastHistoryId; }

    public Instant getLastPollAt() { return lastPollAt; }
    public void setLastPollAt(Instant lastPollAt) { this.lastPollAt = lastPollAt; }

    public int getMessagesIngested() { return messagesIngested; }
    public void setMessagesIngested(int messagesIngested) { this.messagesIngested = messagesIngested; }
}
