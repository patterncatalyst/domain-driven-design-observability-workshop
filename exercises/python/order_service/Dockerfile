# Build context is exercises/python/ (the project root)
FROM registry.access.redhat.com/ubi9/python-314:latest

WORKDIR /opt/app-root/src

# Install dependencies (shared + service-specific)
COPY shared_observability/ ./shared_observability/
COPY order_service/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Copy service code
COPY order_service/ ./order_service/

# PYTHONPATH includes project root so shared_observability is importable
ENV PYTHONPATH=/opt/app-root/src

WORKDIR /opt/app-root/src/order_service
EXPOSE 8080
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
