FROM python:3.9-slim as base

RUN apt-get upgrade \
    && pip install --upgrade pip

WORKDIR /home/connector-builder-server
COPY . ./

RUN pip install --no-cache-dir -r requirements.txt

ENTRYPOINT ["uvicorn", "connector_builder.entrypoint:app", "--host", "0.0.0.0", "--port", "80"]

EXPOSE 5003

LABEL io.airbyte.version=0.44.11
LABEL io.airbyte.name=airbyte/connector-builder-server
