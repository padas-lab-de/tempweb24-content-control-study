name: "robotstxt-parser"

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

  - resource: false
    file: "topology/robotstxt-parser/crawler.yml"
    override: true

spouts:
  - id: "warcspout"
    className: "eu.ows.warc.OWSWARCSpout"
    parallelism: 1
    constructorArgs:
      - ${spouts.input.dir}
      - ${warcspout.paths}

bolts:
  - id: "parser"
    className: "eu.ows.bolt.RobotsParserBolt"
    parallelism: 4
  - id: "index"
    className: "eu.ows.bolt.CustomizedIndexerBolt"
    parallelism: 4

streams:
  - from: "warcspout"
    to: "parser"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "parser"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE
