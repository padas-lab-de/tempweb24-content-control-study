name: "html-parser"

includes:
  - resource: true
    file: "/crawler-default.yaml"
    override: false

  - resource: false
    file: "topology/html-parser/crawler.yml"
    override: true

  - resource: false
    file: "topology/html-parser/opensearch-conf.yml"
    override: true

spouts:
  - id: "warcspout"
    className: "com.digitalpebble.stormcrawler.warc.WARCSpout"
    parallelism: 1
    constructorArgs:
      - ${spouts.input.dir}
      - ${warcspout.paths}

bolts:
  - id: "jsoup"
    className: "com.digitalpebble.stormcrawler.bolt.JSoupParserBolt"
    parallelism: 8
  - id: "index"
    className: "eu.ows.bolt.CustomizedIndexerBolt"
    parallelism: 4

streams:
  - from: "warcspout"
    to: "jsoup"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "jsoup"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE
