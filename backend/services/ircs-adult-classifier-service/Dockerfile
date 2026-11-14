FROM python:3.11-slim
ARG APP_VERSION=3.3.1

ENV APP_VERSION=${APP_VERSION} \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/models \
    APP_ADULT_CLASSIFIER_LOCAL_FILES_ONLY=true

WORKDIR /app

COPY services/ircs-adult-classifier-service/requirements.txt /app/requirements.txt
# This service runs CPU inference on both amd64 and arm64. Avoid CUDA dependencies.
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.15.0 && \
    pip install --no-cache-dir -r /app/requirements.txt

COPY services/ircs-adult-classifier-service/app /app/adult_classifier
RUN python -m adult_classifier.download_model

EXPOSE 8080

CMD ["uvicorn", "adult_classifier.main:app", "--host", "0.0.0.0", "--port", "8080"]
