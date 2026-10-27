FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends squid ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && rm /etc/squid/squid.conf

COPY squid.conf /etc/squid/squid.conf.template
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 3128
ENV SQUID_ALLOWED_CLIENTS=10.0.0.0/24
USER proxy
RUN /usr/local/bin/docker-entrypoint.sh \
    squid -k parse -f /tmp/squid.conf
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["squid", "-N", "-f", "/tmp/squid.conf"]
