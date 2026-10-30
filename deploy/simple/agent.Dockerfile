FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY artifacts/agent-service.whl /tmp/warehouse_agent_service-0.2.0-py3-none-any.whl
RUN pip install --no-cache-dir "/tmp/warehouse_agent_service-0.2.0-py3-none-any.whl[run,rag-runtime]" \
    && rm /tmp/warehouse_agent_service-0.2.0-py3-none-any.whl
USER 65532:65532
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091"]
