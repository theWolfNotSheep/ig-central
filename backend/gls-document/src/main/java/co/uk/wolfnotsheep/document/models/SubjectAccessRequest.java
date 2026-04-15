package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A Subject Access Request (SAR) — an individual's legal right
 * to obtain a copy of their personal data held by the organisation.
 */
@Document(collection = "subject_access_requests")
public class SubjectAccessRequest {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reference; // SAR-2026-001

    private String dataSubjectName;
    private String dataSubjectEmail;
    private List<String> searchTerms;

    private String requestedBy;
    private Instant requestDate;
    private Instant deadline;

    @Indexed
    private String status; // RECEIVED, SEARCHING, REVIEWING, COMPILING, COMPLETED, OVERDUE

    private String jurisdiction; // UK_GDPR, CCPA, HIPAA

    private List<String> matchedDocumentIds;
    private int totalMatches;

    private List<SarNote> notes;

    private String assignedTo;
    private Instant completedAt;

    public SubjectAccessRequest() {}

    public record SarNote(String text, String author, Instant timestamp) {}

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getDataSubjectName() { return dataSubjectName; }
    public void setDataSubjectName(String dataSubjectName) { this.dataSubjectName = dataSubjectName; }

    public String getDataSubjectEmail() { return dataSubjectEmail; }
    public void setDataSubjectEmail(String dataSubjectEmail) { this.dataSubjectEmail = dataSubjectEmail; }

    public List<String> getSearchTerms() { return searchTerms; }
    public void setSearchTerms(List<String> searchTerms) { this.searchTerms = searchTerms; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public Instant getRequestDate() { return requestDate; }
    public void setRequestDate(Instant requestDate) { this.requestDate = requestDate; }

    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public List<String> getMatchedDocumentIds() { return matchedDocumentIds; }
    public void setMatchedDocumentIds(List<String> matchedDocumentIds) { this.matchedDocumentIds = matchedDocumentIds; }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

    public List<SarNote> getNotes() { return notes; }
    public void setNotes(List<SarNote> notes) { this.notes = notes; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
