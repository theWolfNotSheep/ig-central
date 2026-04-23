# ML Reports & Scatter Analysis — Demo Script

**Duration: 2:45**

**Goal:** Show how the Reports section provides visibility into model performance, classification quality, training data health, AI cost, human feedback effectiveness, and individual data point analysis through scatter charts.

## Script

1. **Navigate to Reports** — Click "Reports" in the admin sidebar (bar chart icon). *"The Reports section gives you full visibility into how the ML pipeline is performing — from model accuracy to cost trends to human feedback loops."*

2. **Model Performance tab** — Show the default tab with:
   - Summary cards: training runs, active model, best accuracy, best F1
   - Accuracy & F1 line chart over training versions
   - Sample count bar chart per version
   
   *"Each time BERT is retrained, the accuracy and F1 score are tracked. You can see the model improving as more training data accumulates — version over version."*

3. **Click a version in the chart** — Click on a data point in the accuracy chart. The per-class metrics table appears below showing precision, recall, F1 per category. *"Click any version to drill into per-class performance. This immediately shows which categories BERT handles well and which need more training data."*

4. **Scroll to BERT hit rate** — Show the stacked area chart (BERT vs LLM) and the hit rate percentage line. *"The hit rate chart shows what percentage of documents BERT handles without needing the LLM. This is the north star metric — as it rises, classification gets faster and cheaper."*

5. **Switch to Classification Quality tab** — Click the tab. Show:
   - Confidence distribution histogram (BERT vs LLM bars)
   - Sensitivity pie chart
   - Top categories horizontal bar chart
   
   *"The confidence distribution reveals calibration. If BERT clusters all predictions at 0.99, it's overconfident. A healthy model spreads across the range. The LLM histogram beside it shows the comparison."*

6. **Switch to Training Data tab** — Click the tab. Show:
   - Samples per category stacked bar (auto-collected, manual, correction, bulk)
   - Graduation progress bars
   - Cumulative growth area chart
   
   *"Training data health at a glance. The stacked bars show where your data comes from per category. The graduation progress shows which categories have enough samples to be learned by BERT — and which are still below threshold."*

7. **Switch to Cost & Efficiency tab** — Click the tab. Show:
   - Daily cost trend
   - Token usage (input vs output stacked area)
   - Provider and model breakdown cards
   
   *"Track AI spend over time. As BERT takes over more classifications, the daily cost line should trend downward. The provider breakdown shows the split between cloud API and local Ollama."*

8. **Switch to Feedback Loop tab** — Click the tab. Show:
   - Override rate warning (if >20%)
   - Correction type pie chart
   - Confusion pairs list
   - Override rate trend with 20% threshold line
   
   *"The feedback loop tracks every human correction. The override rate is the key drift indicator — when it crosses 20%, the model needs retraining. The confusion pairs show exactly which categories are being mixed up, guiding targeted data collection."*

9. **Switch to Scatter Analysis tab** — Click the tab. Show:
   - Confidence vs Latency scatter (coloured by provider)
   - Samples vs F1 scatter
   
   *"Scatter charts reveal relationships that aggregate charts hide. Here: does the model take longer on uncertain classifications? Each dot is a single AI call. And below — does more training data actually improve per-category accuracy? Points bottom-left need more data; points top-right are well-trained."*

10. **Hover over outliers** — Hover on a point in the Tokens vs Cost chart showing the custom tooltip with full detail. *"Hover any point for full context — tokens consumed, model used, latency, provider. Spot outlier calls that burned disproportionate resources."*

**Key message:** *"Full observability of your ML pipeline — from model improvement trajectories to individual classification calls. Every chart answers a specific question: is the model improving, where is it weak, what's it costing, and is it drifting?"*
