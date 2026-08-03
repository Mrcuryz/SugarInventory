FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY artifacts/agent-service.whl /tmp/agent-service.whl
RUN pip install --no-cache-dir "/tmp/agent-service.whl[run,rag-runtime]" && rm /tmp/agent-service.whl
USER 65532:65532
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091"]
