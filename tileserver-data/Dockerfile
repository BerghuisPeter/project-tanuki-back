FROM maptiler/tileserver-gl:latest

COPY data /data
COPY styles /styles
COPY config.json /config.json

CMD ["--config", "/config.json"]