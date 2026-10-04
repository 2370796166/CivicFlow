FROM python:3.12-slim@sha256:f77ac9e44ae96ef2c90b8053ea08c31f8be030f824196b0ae4db6d462c84e51f
WORKDIR /bootstrap
COPY scripts/requirements-e2e.txt deploy/docker/requirements.txt ./
RUN pip install --no-cache-dir --use-deprecated=legacy-resolver -r requirements-e2e.txt -r requirements.txt && pip check
COPY scripts/dev.py ./
COPY deploy/docker/bootstrap.py ./
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "/bootstrap/bootstrap.py"]
