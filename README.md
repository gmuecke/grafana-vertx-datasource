# grafana-vertx-datasource
Example Datasource for Grafana, based on Vert.x and MongoDB

## About Grafana
Grafana is an open source tool for visualizing timeseries data. It runs as a server and the UI is accessible through
a browser. It allows to create dashboards with various visualization options and other panels. Timeseries data can 
be visualized in various time frames, including real time.

## Timeseries
Timeseries data is a set of datapoints that have at least the two properties:
- point in time, i.e. a time stamp, date, time etc. Relates to the x-axis
- scalar value, relates to the y-axis and could be any scalar value, count, sum, avg, response times, occurrences etc.

Every Datapoint could have additional characteristics that could be used as labels, names etc.

Based on this data, further calculations can be done, i.e. calculating averages, percentiles, etc.



# Grafana Data Format

A Grafana Datasource requires three service endpoints

- `/search` searches for labels or targets of the timeseries to visualize 
- `/annotations` searches for markers on the timeline with specific information
- `/query` searches for the actual datapoints of the timeseries

## Search / Targets
The search service, accepting post-request at the `/search` endpoint, provides the _names_ of the time series.

### Request


    {
      "target" : "select metric",
      "refId" : "E"
    }
    
- `target` the value of the select-metric field
- `refId` the name of the metrics reference (shown left of the metrics name in the UI) 

### Response

A list of metric names, to be used as _targets_ in query requests

    [
        "Metric Name 1",
        "Metric Name2",
    ]

## Annotations

Annotations can be used to indicate specific points in the time and provide additional values.
The Annotation endpoint `/annotations` accepts post-requests.

### Request
    {
      "annotation" : {
        "name" : "Test",
        "iconColor" : "rgba(255, 96, 96, 1)",
        "datasource" : "Simple Example DS",
        "enable" : true,
        "query" : "{\"name\":\"Timeseries A\"}"
      },
      "range" : {
        "from" : "2016-06-13T12:23:47.387Z",
        "to" : "2016-06-13T12:24:19.217Z"
      },
      "rangeRaw" : {
        "from" : "2016-06-13T12:23:47.387Z",
        "to" : "2016-06-13T12:24:19.217Z"
      }
    }

- `annotation` a description of the annotation to search for markers. The annoation object is returend in the response
  - `name` the name of the annotation description
  - `iconColo` the color of the annotation of the chart
  - `datasource` the name of the datasource to look for annotations. This can be used to correlate annoations from one
     datasource with timeseries data of another.
  - `enable` flag to indicate if the annoation should be displayed
  - `query` the search query to look up annoations. This is an opaque string. The given example contains a mongo find 
  query
- `range` the time range in which to search for the annotations in ISO date format
- `rangeRaw` a textual representation of the range, could be a period expression as well, like `3w`

### Response

An array of annoations.

    [
      {
        "annotation": {
          "name": "Test",
          "iconColor": "rgba(255, 96, 96, 1)",
          "datasource": "Simple Example DS",
          "enable": true,
          "query": "{\"name\":\"Timeseries A\"}"
        },
        "time": 1465820629774,
        "title": "Marker",
        "tags": [
            "Tag 1",
            "Tag 2"
        ]
      }
    ]

- `annotation` the original annotation description
- `time` the timestamp in ms when the annotation should be shown
- `title` the title or name of the displayed annotation
- `tags` an array of string of additional tags of this annotation

## Time Series Datapoint

The query service, accepting post-requests at the `/query` endpoint, delivers the actual datapoints matching
the time range and the target selection.

### Request

    {
      "panelId" : 1,
      "maxDataPoints" : 1904,
      "format" : "json",
      "range" : {
        "from" : "2016-06-13T12:23:47.387Z",
        "to" : "2016-06-13T12:24:19.217Z"
      },
      "rangeRaw" : {
        "from" : "2016-06-13T12:23:47.387Z",
        "to" : "2016-06-13T12:24:19.217Z"
      },
      "interval" : "20ms",
      "targets" : [ {
        "target" : "Time series A",
        "refId" : "A"
      }, {
        "target" : "Time series B",
        "refId" : "B"
      }]
    }

- `panelId` the id of the panel
- `maxDataPoints` number of maximum datapoints displayed
- `format` json
- `range` is data time of the displayed time frame
- `rangeRaw` a raw representation of the rage, could also be something like `3w` for 3 weeks
- `interval` the interval of the datapoints in a valu-timeunit String representation 
- `intervalMs` the interval of the datapoints in ms
- `targets` an array of target object that should be displayed on the current panel

### Response

An Array of Objects, each with a `datapoints` and a `target` property. The datapoints property  is an array of
arrays with each sub-array containing two values, first is the datapoint value, the second is the timestamp (java 
timestamp in ms).

    [
        {
            "target":"Timeseries A",
            "datapoints":[
                [1936,1465820629774],
                [2105,1465820632673],
                [4187,1465820635570],
                [30001,1465820645243]
            ]
        },
        {
            "target":"Timeseries B",
            "datapoints":[]
        }
    ]

# The Demos

Following a short description of the demos in this project.

## Demo 1

Run the `io.devcon5.metrics.demo1.SimpleGrafanaDatasource` 
it shows the basic deployement of verious verticles, each fulfilling it's own purpose.
The datasource can be configured to some extend (mongo coordinate, http port).

Querying for timeseries datapoints using this example can be long for longer durations as it is only processed 
single-threaded.

## Demo 2

Run the `io.devcon5.metrics.demo2.ScaledGrafanaDatasource` 
it shows how verticles can be scaled. This example processes the timeseries query in parallel. It splits the
request, sends each chunk request to query verticles (actually the same as in Demo1). If there is only one
verticle deployed, it'll process all the messages sequentially. Once all chunk requests have been responded to
each individual result is merged again into one single result and returned to grafana.
The example deploys as many verticles for processing the chunk requests as there are CPU cores on the machine. And
each query request is split into the same amount of chunk requests. Each chunk request represents a sub range of
the initial time range.

## Demo 3
This is a variation of Demo 2, but with a distributed event bus. Although this can run locally, the distributed
event bus can stretch accross multiple machines, allowing to process even large datasats.

