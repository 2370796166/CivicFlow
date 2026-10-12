FROM python:3.12-slim@sha256:f77ac9e44ae96ef2c90b8053ea08c31f8be030f824196b0ae4db6d462c84e51f
WORKDIR /bootstrap
COPY deploy/local/requirements.txt ./requirements-local.txt
COPY deploy/docker/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --use-deprecated=legacy-resolver -r requirements-local.txt -r requirements.txt && pip check
COPY deploy/local/dev.py ./deploy/local/dev.py
COPY deploy/docker/bootstrap.py ./deploy/docker/bootstrap.py
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "/bootstrap/deploy/docker/bootstrap.py"]
