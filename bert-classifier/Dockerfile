FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY . .

# Download default model on build (distilbert-base-uncased)
# This is a placeholder — replace with a fine-tuned model for production
RUN python -c "from transformers import AutoTokenizer; AutoTokenizer.from_pretrained('distilbert-base-uncased')"

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
