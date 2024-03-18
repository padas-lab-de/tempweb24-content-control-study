# A Longitudinal Study of Content Control Mechanisms
This repository contains the source code for the paper "A Longitudinal Study of Content Control Mechanisms" by Michael Dinzinger and Michael Granitzer, which was presented at the Temporal Web Analytics Workshop hosted at the ACM Web Conference 2024.

[![Results](https://avatars.githubusercontent.com/u/46666731?s=50&v=4)](https://padas-lab-de.github.io/robotstxt-study/)

## Installation
The Java project in the repository implements two technical pipelines for parsing and indexing web documents, using the [Stormcrawler](https://stormcrawler.net) software framework.

### Prerequisites
- Java 11
- Maven
- Docker

### Caveats
- This installation guide spawns a dockerized Apache Storm cluster as well as a dockerized OpenSearch instance. Note that you can also run the Stormcrawler on a locally hosted Storm cluster. Furthermore, you can store the extracted meta information in a local or remote non-dockerized OpenSearch instance. Modify therefore the `dev.properties` file.
- The extraction of HTML annotations and Creative Commons outlinks is implemented using the existent Stormcrawler component `XPathFilter`. For extracting HTTP Response Headers, we use the customized Stormcrawler component `HTTPResponseHeaderFilter` (see [implementation](/src/main/java/eu/ows/parse/filter/HTTPResponseHeaderFilter.java)).
- The extracted metadata is stored in OpenSearch using the Stormcrawler's OpenSearch module. The [IndexerBolt](https://github.com/DigitalPebble/storm-crawler/blob/master/external/opensearch/src/main/java/com/digitalpebble/stormcrawler/opensearch/bolt/IndexerBolt.java) filters by default all documents containing a `noindex` HTML meta tag. That is why we opted for using a `CustomizedIndexerBolt` (see [implementation](/src/main/java/eu/ows/bolt/CustomizedIndexerBolt.java)), which extends the default implementation and overrides the `filterDocument` method. This allows us to include all web documents in our research study, also the ones marked as `noindex`. `CustomizedIndexerBolt` furthermore overrides the `filterMetadata` method to filter out all metadata fields that are of interest for the research study using the asterisk expressions, e.g. `parse.meta.*`.
- We use the [WARCSpout](https://github.com/DigitalPebble/storm-crawler/blob/master/external/warc/src/main/java/com/digitalpebble/stormcrawler/warc/WARCSpout.java) for downloading WARC files from the publicly available Common Crawl web archive. The WARC path files can be found [here](https://commoncrawl.org/overview) and are placed in the `./input` folder in the root directory of the project.
- The project requires the latest version of StormCrawler (`12-SNAPSHOT`) to be installed locally using Maven.

### Topologies
- `html-parser`: Parsing pipeline for extracting meta information from **HTML annotations** and **HTTP Response Headers**. This topology is used for parsing the Common Crawl WARC files.
- `fetch-html-parser`: In contrast to `html-parser`, this topology additionally fetches and parses the robots.txt and tdmrep.json file corresponding to the URL extracted from the Common Crawl WARC files.
- `robotstxt-parser`: Parsing pipeline for **robots.txt** files.

### Guide
1. Place the unzipped `warc.paths` file, corresponding to a CommonCrawl dump, in the `./input` folder in the root directory of the project.
```
wget https://data.commoncrawl.org/crawl-data/CC-MAIN-2024-10/warc.paths.gz
gunzip warc.paths.gz
./prepend-script.sh ./warc.paths
```
2. Compile the `commoncrawl-parser` project using Maven.
```
mvn clean package
```
3. Modify the config parameters in the `dev.properties` file.
```
nano dev.properties
```
4. (Optional) Override the default values of Apache Storm, Zookeeper or Opensearch by specifying it in an environment variable e.g.
```
export STORM_VERSION=2.6.0
export OPENSEARCH_VERSION=2.8.0
export ZOOKEEPER_VERSION=3.6.3
```
5. Set up a dockerized Apache Storm cluster.
```
docker-compose -f docker-compose.yml up -d --build
```
6. Set up a dockerized OpenSearch instance.
```
docker-compose -f docker-compose-os.yml up -d --build
```
Wait a few minutes until OpenSearch is up and running then initialise the index with 
```
./OS_InitIndex.sh
```
7. Run the `crawler` Docker container and submit the `crawler.flux` file of the `html-parser` topology to the Apache Storm cluster.
```
docker-compose run --rm crawler storm jar crawler.jar org.apache.storm.flux.Flux topology/html-parser/crawler.flux --filter dev.properties
```

### Example
The following JSON document shows the metadata extracted and stored for an examplary URL. Note that it contains general information about the web document, as well as the extracted HTML annotations and HTTP Response Headers. General information concerns e.g., `capturetime`, `url`, `domain`, `host`, `title`, `description`, `keywords`, `feedlink` and `language`. HTML annotations are in this example the Robots meta tags (stored in `parse.meta`, such as e.g., `parse.meta.robots`), whereas HTTP Response Headers are stored in `parse.http` (e.g. `parse.http.x-robots-tag`). For a better retracability, we also store the WARC file name and the offset of the WARC record (see `warc.file.name` and `warc.record.offset`).
```json
{
    "capturetime": "1695289671000",
    "url": "https://historia.nationalgeographic.com.es/a/propaganda-durante-guerra-cuba_19919",
    "domain": "nationalgeographic.com.es",
    "host": "historia.nationalgeographic.com.es",
    "title": "¿Cuá fue el papel de la prensa durante la guerra de Cuba?",
    "description": "La propaganda americana durante la guerra de Cuba",
    "keywords": [
      "National",
      "Geographic",
      "Ciencia",
      "Naturaleza",
      "Historia",
      "Viajes"
    ],
    "parse.feedlink": "/feeds/a",
    "parse.html.language": "es",
    "warc.file.name": "https://data.commoncrawl.org/crawl-data/CC-MAIN-2023-40/segments/1695233505362.29/warc/CC-MAIN-20230921073711-20230921103711-00000.warc.gz",
    "warc.record.offset": "328831544",
    "parse.http.x-robots-tag": "all",
    "parse.meta.robots": [
      "max-image-preview:large",
      "max-video-preview:-1"
    ],
    etc.
}
```

## Promo Video


<details><summary>Transcript</summary>
Our study is concerned with the question how web publishers can control for what and under which conditions their content is allowed to be used. It is motivated but the recent breakthrough of generative AI. The rise of this technology yielded a number of ad hoc standards for the opt-out from generative AI training. These are, for instance, the Google-Extended user agent, the NoML meta tag proposed by the Search Engine Mojeek and the TDM Reservation Protocol. To put it in a bigger picture, these are recent measures of web content control in response to an increased awareness of web publishers' data sovereignty. As generative AI models or capable of imitating and reproducing its training data, which is mostly web data, this sovereignty is at serious risk. In our work, we study the prevalent measures of web content control inform of a longer to the analysis. This may help us to better answer which are the prevalent mechanisms and how well are they adopted among the practitioners community as well as to better understand the transition of web content control caused by generative AI.

Conceptually, there are two ways of web content control:

- First, the regulation of web agents (or crawlers), autonomous bots that automatically traverse the enormous web of hyperlinks and harvest online documents. These documents are further indexed by Search Engines or prepared as data products, e.g., datasets used in the training of Machine Learning and AI models. The commonly agreed standard for the regulation of web robots is the Robots Exclusion Protocol. The protocol, which was initially introduced already 30 years ago, therefore place a central role in the ecosystem of the web.

- The second means to apply control over web content is the annotation with licensing information. The delivered HTML documents offer license-related semantic markup that indicate the terms and conditions of use to any robotic data consumers.

We therefore analyzed eight publicly available crawl dumps from Common Crawl, which have been collected between 2016 and - for the moment that this video is recorded - December 2023. For studying the prevalence of web content control in the wild, we parsed both the available dumps for robots.txt files, containing between 58M and 92M documents, as well as for regular web pages. The robots.txt dumps were parsed completely, where as for regular dumps we restricted the analysis to the first 60M documents in the WARC files due their large size.

Two of the key results of the empirical study are the following:

The user agents this allowed and the robots.txt files reveal a clear stance against crawlers feeding AI models (e.g. GPTBot) and misbehaving crawlers (e.g. PetalBot). Regarding the annotation of licensing information
</details>

## Cite
```bib
@inproceedings{Dinzinger2024,
  series = {WWW ’24},
  title = {A Longitudinal Study of Content Control Mechanisms},
  url = {https://doi.org/10.1145/3589335.3651893},
  DOI = {10.1145/3589335.3651893},
  booktitle = {Companion Proceedings of the ACM Web Conference 2024},
  publisher = {ACM},
  author = {Dinzinger, Michael and Granitzer, Michael},
  year = {2024},
  month = may,
  collection = {WWW ’24}
}
```
