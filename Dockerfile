FROM storm:${STORM_VERSION:-2.6.0}

RUN apt-get update -qq && \
	apt-get install -yq --no-install-recommends \
		curl \
		jq \
		less \
		vim

# Owler
ENV CRAWLER_VERSION=1.0-SNAPSHOT
RUN mkdir /crawler && \
    chmod -R a+rx /crawler

# add the crawler uber-jar
COPY target/commoncrawl-parser-$CRAWLER_VERSION.jar /crawler/crawler.jar

# and topology configuration files
COPY topology/ /crawler/topology/

# and the dev.properties file
COPY dev.properties /crawler/dev.properties

RUN chown -R "storm:storm" /crawler/

USER storm
WORKDIR /crawler/