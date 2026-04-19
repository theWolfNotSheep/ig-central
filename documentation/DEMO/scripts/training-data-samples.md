# Training Data Samples — Demo Script

**Duration: 2:00**

**Goal:** Show how the platform automatically curates a labelled training dataset from live classifications and corrections — ready to fuel BERT fine-tuning or export for external ML teams.

## Script

1. **Open with the value.** *"Every classified document in your platform is a labelled data point — filename, text, category, sensitivity, metadata, PII findings. Most platforms throw that away. We turn it into the most valuable training corpus your organisation owns."*

2. **Navigate to AI → Training Data.**

3. **Show the sample list.** *"Each row is a sample — document text, LLM's classification, confidence, and status. Status can be Auto-accepted (high confidence), Needs review, Human-verified, or Rejected."*

4. **Walk the auto-acceptance rules.** *"Set the auto-accept threshold — say 95% confidence — and every classification above that lands as a high-quality sample automatically. Below that threshold, samples go into the verification queue."*

5. **Show the verification queue.** *"Your records manager works through the queue — quick yes/no per sample. Reject obvious misclassifications, accept the rest. A good verifier can get through hundreds an hour."*

6. **Show per-category balance.** *"Samples grouped by category with a count bar. If one category is under-represented, you know to spend review time there — critical for model accuracy on low-volume categories."*

7. **Export options.** *"Export the curated dataset as JSONL, Parquet, or CSV — for external training pipelines, academic research, or your own data science team. Or leave it in place for BERT fine-tuning."*

8. **Highlight the compounding effect.** *"Every day your corpus grows. Every correction refines it. Six months in, you have a training set most AI vendors would pay for — and it's yours."*

**Key message:** *"Your production classifications compound into a proprietary training asset — the platform curates it, your team verifies it, and it fuels everything from local models to analytics."*
