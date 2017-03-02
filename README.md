# grafana-vertx-datasource
Example Datasource for Grafana, based on Vert.x and MongoDB. 

This project contains the demos for the talk at TopConf Linz 2017.
The slides for the talk are here: [https://www.slideshare.net/GeraldMuecke/making-sense-of-your-data]

## Disclaimer

The source code was written for demo purposes only and is not meant for production use. Neither has it any tests or
other quality assuring means applied. So use with care and on your own risk.


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

## Demo 1 - Retrieving Datapoints

Run the `io.devcon5.metrics.demo1.SimpleGrafanaDatasource` 
it shows the basic deployement of verious verticles, each fulfilling it's own purpose.
The datasource can be configured to some extend (mongo coordinate, http port).

Querying for timeseries datapoints using this example can be long for longer durations as it is only processed 
single-threaded.

## Demo 2 - Splitting Requests into chunks

Run the `io.devcon5.metrics.demo2.ScaledGrafanaDatasource` 
it shows how verticles can be scaled. This example processes the timeseries query in parallel. It splits the
request, sends each chunk request to query verticles (actually the same as in Demo1). If there is only one
verticle deployed, it'll process all the messages sequentially. Once all chunk requests have been responded to
each individual result is merged again into one single result and returned to grafana.
The example deploys as many verticles for processing the chunk requests as there are CPU cores on the machine. And
each query request is split into the same amount of chunk requests. Each chunk request represents a sub range of
the initial time range.

#### Why does the number of instances does not improve the overall response time?
Because most of the time is lost in querying the DB. The larger the result set, the longer it takes. Merging the
data back into one result set is relatively fast. By splitting up the request into multiple sub requests, the DB querying
can be done in parallel, which speeds up the overall process. And because the actor for processing the sub-requests
is waiting most of the time for the DB, a single actor can process all the sub-requests alone.

Most of the CPU time however, is spent on the DB side. The Vert.x datasource waits most of the time for the responses
so it doesn't matter, how many instances are deployed - apart from HA capabilities (see next demo).

Further, experiments indicate, the optimum in response time can be achieved, by creating as many chunks as there are
physical cores available to process the parallel queries on the DB side. I.e. use 8 chunks on a 8 core CPU.

## Demo 3 - Clustering
This is a variation of Demo 2, but with a distributed event bus. Although this can run locally, the distributed
event bus can stretch accross multiple machines, allowing to process even large datasats.

## Demo 4 - Aggregation
The Grafana query requests contain two values which have not been processed so far - interval and maxDatapoints.
So from a Grafana perspective we don't need more than the requested datapoints. But we could use this
information to reduce the amount of data retrieved and returned by aggregating datapoints within an interval.
For this purpose we can leverage the database capabilities, specifically the aggregation framework of MongoDB

## Demo 5 - Percentiles
Unlike the retrieval of the datapoints, calculating the percentiles for the entire range requires some computation.
As computational tasks may easily block the event loop, we delegate this to worker verticles that run in a 
dedicated thread.

## Demo 6 JS Verticles
Demonstrates how to write verticles in Javascript and apply percentile calculation using the aggregation framework.

## Demo 7 Clusterin & Postprocessing
Demonstrates how postprocessing is applied to datapoint by mulitplying the values with the historical bitcoin price
(time is money :) and how this postprocessor is attached via clustering / distributed eventbus.
