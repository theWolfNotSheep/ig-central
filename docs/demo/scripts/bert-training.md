# BERT Fine-Tuning Dashboard — Demo Script

**Duration: 3:00**

**Goal:** Show how customers can graduate from LLM-only classification to a fine-tuned BERT model — trained on their own corrections — cutting cost and latency dramatically.

## Script

1. **Open with the progression.** *"LLMs are brilliant at bootstrapping — you get great classification from day one with no training data. But after six months, you've accumulated thousands of classified documents and thousands of corrections. That's a gold mine. We turn it into a model that runs for pennies per thousand documents, locally, in milliseconds."*

2. **Navigate to AI → BERT Training.**

3. **Show the training data overview.** *"Every high-confidence classification your LLM has made, plus every correction your reviewers have made, is a labelled training sample. You see the total sample count, the category distribution, and the class balance — critical for model accuracy."*

4. **Show the sample verification queue.** *"Before training, your records manager can spot-check samples — accept, reject, or relabel. This keeps the training set clean. You can verify in bulk or by category."*

5. **Kick off a training job.** *"Pick the base model — ModernBERT by default, proven for document classification. Set the hyperparameters — or take the defaults. Click Train."*

6. **Walk the job status view.** *"The job runs in the background — usually an hour or two on a GPU node. You see the epochs, loss curve, validation accuracy, per-category F1 score. Full transparency, no black box."*

7. **Show model evaluation.** *"When training completes, the new model is evaluated against a held-out test set. You see precision, recall, F1 per category. If the model underperforms on a specific category, you know exactly where to focus."*

8. **Promote to production.** *"Click Promote. The fine-tuned BERT becomes the first-pass classifier — fast, cheap, local. The LLM stays in the loop as a fallback for low-confidence results or edge cases."*

9. **Show the cost impact.** *"Before fine-tuning, your cost per thousand documents was maybe £3-5. After — pennies. Latency drops from two seconds to under a hundred milliseconds. For high-volume estates, that's transformative."*

**Key message:** *"Your classification platform gets smarter, faster, and cheaper over time — trained on your data, owned by you, no vendor lock-in on the inference."*
