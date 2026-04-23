# Google Drive Labels & Taxonomy Bridge — Demo Script

**Duration: 3:00**

**Goal:** Show how existing Google Workspace labels on Drive files can be read, mapped to ISO 15489 BCS categories, written back after classification, and used to bootstrap BERT training data at zero LLM cost.

## Script

1. **Navigate to Drives** — Open `/drives` in the sidebar. *"IG Central connects to Google Drive and classifies documents in place. But many organisations already have labels applied to their files — category tags, lifecycle status, confidentiality markers. We can read those labels and use them."*

2. **Show label badges on files** — Browse into a folder with labeled files. Purple badges appear next to files that have Workspace labels. *"Here you can see files that already have Google Workspace labels applied. The system reads these automatically via the Drive API when listing files."*

3. **Open Labels configuration** — Click the gear/settings icon on the connected drive, then click "Labels". *"Let's configure the label-taxonomy bridge. This is where we connect existing Drive labels to our ISO 15489 Business Classification Scheme."*

4. **Select the Workspace label** — The page shows available labels discovered from your files. Click the "Lifecycle Status" label (or whichever label appears). *"The system discovers labels by reading them from your files. Here's the lifecycle status label that's already applied to documents in this Drive."*

5. **Configure field mapping (outbound)** — Map GLS fields to label fields: Category → the category field, Sensitivity → the sensitivity field, Classification Code → a text field. *"Field mapping controls what gets written back to Drive after classification. When IG Central classifies a document, it updates the Drive label automatically — so other users see the classification directly in Google Drive."*

6. **Save the field mapping** — Click "Save Configuration". *"That handles the outbound direction — classification results flowing to Drive labels."*

7. **Scroll to Taxonomy Mapping (inbound)** — The purple "Label → Taxonomy Mapping" section appears below. *"Now the powerful part: mapping existing label values to BCS categories. This creates a bi-directional bridge."*

8. **Select the classification field** — Pick which label field contains the category information (e.g., the selection field with values like "Finance", "HR", "Legal"). *"We tell the system which field in the label carries the classification information."*

9. **Map label values to BCS categories** — Click "+ Add value mapping" several times. For each:
   - Type "Finance" → select `[FIN] Finance & Accounting (FUNCTION)` from the dropdown
   - Type "HR - Recruitment" → select `[HR-REC] Recruitment (ACTIVITY)`
   - Type "Invoice" → select `[FIN-AP-INV] Invoice Processing (TRANSACTION)`
   
   *"Each label value maps to a specific point in the taxonomy — at any level. 'Finance' maps broadly to the function. 'HR - Recruitment' maps to a specific activity. 'Invoice' maps all the way down to the transaction level. The system handles all three."*

10. **Configure sync settings** — Show the Sync Direction (Bidirectional), Conflict Policy (Flag for review), and "Collect as BERT training data" checkbox (enabled). *"We run bidirectional sync — labels flow in and classification flows out. When they disagree, it goes to the review queue. And crucially, every mapped document becomes BERT training data automatically."*

11. **Set confidence** — Show the confidence slider at 0.90. *"We assign 0.90 confidence to label-sourced classifications. Someone applied that label deliberately — it's a strong signal, but not absolute certainty."*

12. **Save the taxonomy mapping** — Click "Save Taxonomy Mapping". *"The mapping is saved. From now on, every file registered from this drive will have its labels checked against this mapping."*

13. **Bulk import** — Click "Import Existing Labels as Training Data". Wait for the result toast (e.g., "Import complete: 47 mapped, 43 collected as training data (120 scanned)"). *"Here's the payoff. We just imported 43 pre-classified training samples from existing Drive labels — at zero LLM cost, in seconds. That's data that would have taken hours to produce through the classification pipeline."*

14. **Show training data** — Navigate to AI > Models. Show the training data stats — the samples-by-source breakdown now includes "DRIVE_LABEL" entries. *"In the models dashboard you can see the new training samples sourced from Drive labels. These feed directly into BERT training alongside LLM-classified and human-corrected samples."*

15. **Demonstrate pre-classification on new file** — Go back to Drives, select an unclassified file that has a label, register it for classification. Show that the document immediately gets a pre-classified category badge before the pipeline even runs. *"When we register a new file, the system checks its existing label, finds the mapping, and pre-classifies it immediately. The document enters the pipeline with a category already assigned — BERT and the LLM can confirm rather than classify from scratch."*

**Key message:** *"Existing Google Drive labels are free training data. The taxonomy bridge turns years of human labelling work into an instant bootstrap for BERT — no LLM calls, no manual review, just a five-minute mapping configuration."*
