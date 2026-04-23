# Failure Handling & Recovery — Demo Script

**Duration: 3:00**

**Goal:** Show the platform's unhappy-path behaviour — how failed documents are captured, surfaced, and retried without data loss or silent drops.

## Script

1. **Open with the reality.** *"Every pipeline fails sometimes. LLM timeouts, text extraction errors, transient network issues. The question isn't 'will it fail' — it's 'what happens when it does'. Most platforms either drop documents silently or requeue forever. Neither is acceptable. Here's what we do."*

2. **Set up a controlled failure.** *"For this walkthrough, I've temporarily stopped one of the worker services so I can show a real failure, not a simulation."*

3. **Upload a document.** Watch it progress. *"Text extraction succeeds, but classification fails — the LLM worker is offline. Without proper error handling, this document would either be silently lost or stuck forever."*

4. **Navigate to the document in the documents list.** *"Instead, the document is visible with a clear status — CLASSIFICATION_FAILED. Not hidden, not stuck in spinning limbo. The record is explicit."*

5. **Open the document detail.** *"You see exactly what went wrong — the error message, the stage it failed at, the timestamp of failure, and the retry count."*

6. **Navigate to Monitoring.** *"The monitoring dashboard aggregates all failures. Failed document count, a live feed showing each failure with its cause, and the pipeline stage where it happened."*

7. **Demonstrate 'Retry Failed'.** *"Click Retry Failed — every document in a failed state is re-queued. The retry count increments, so you can see the history. A document that's failed three times gets flagged for manual investigation rather than retrying forever."*

8. **Demonstrate stale detection.** *"Documents stuck in an in-flight state for more than ten minutes — PROCESSING, CLASSIFYING — are marked stale. The platform never assumes they're still running; it lets you reset them from the monitoring page."*

9. **Restart the worker.** *"Now let's bring the worker back up."* Trigger retry. *"The document flows through, completes classification, and lands governance-applied. Full audit trail — upload, fail, retry, succeed."*

10. **Show the audit view.** *"Every transition is logged — including the failures and the recovery. Your ops team has evidence of every document's journey, no matter how many retries it took."*

**Key message:** *"Failures are first-class citizens. Nothing is dropped, nothing is hidden, and nothing requires surgery to recover — your ops team has clear tools and a complete audit trail."*
