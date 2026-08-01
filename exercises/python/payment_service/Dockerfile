# =============================================================================
# Payment Service - multi-stage build on UBI 10 Python 3.14
# =============================================================================

FROM registry.access.redhat.com/ubi10/python-314:latest AS builder

WORKDIR /opt/app-root/src
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .

FROM registry.access.redhat.com/ubi10/python-314:latest

WORKDIR /opt/app-root/src
COPY --from=builder /opt/app-root/lib /opt/app-root/lib
COPY --from=builder /opt/app-root/src .

EXPOSE 8082

CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8082"]
