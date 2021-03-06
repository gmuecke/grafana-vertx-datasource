Build Project
=============

    mvn package
    
Demo Database
=============
  
    mongo --shell mongo_tools.js

    db.measurements.findOne()
    db.measurements.count()
  
    countDistinct()
    
    db.measurements.distinct("t.name")
    .map(function(name){return { name : name, count : db.measurements.count({"t.name":name})}})
    .sort(function(o1,o2){return o1.count < o2.count})
    .forEach(function(o){print(JSON.stringify(o)}))
    
    
    measure_20_avg
    measure_3_avg
    measure_4_avg
    measure_5_avg
    measure_7_avg
    
    measure_33_avg

    
Demo Preparation
================

Set the classpath to include the project jar

    export CLASSPATH=target/grafana-vertx-datasource-1.3.jar
    
or

    set CLASSPATH=target/grafana-vertx-datasource-1.3.jar

Demo 1 (Simple)
======

Start verticle on CLI (just for demo)
    
    vertx run io.devcon5.metrics.demo1.SimpleGrafanaDatasource --cp target/grafana-vertx-datasource-1.3.jar --conf config/demo1.json
    
Continue in IDE (for debugging/logging)
    
- show http://localhost:3339
- goto grafana http://localhost:3000
- create datasource
- create dashboard
- add graf panel
- add simple json datasoure
- zoom out to 1 yr

- show "EEV_Start"
- show "KW001_Partner_Zahlungsprofil-erfassen_Weiter klicken mit Dialoghandling und check_Weiter" (~1 Mio entried)

Demo 2 (Chunking / Parallel processing)
======

    vertx run io.devcon5.metrics.demo2.ScaledGrafanaDatasource --cp target/grafana-vertx-datasource-1.3.jar --conf config/demo2.json

- use perform to check CPU by process
- mongod is dominant consumer
- demo with parallelism settings: 2, 4, 8 (best), 16 (same as singethreaded)

Optional: Demo 3 (Clustering)
======

- an example of premature optimization

Demo 4
======

Aggregation example

- discuss & show aggegation pipeline
- explain helper methods
- use different chunks sizes (1... 8.... 24 ... 80) and show effect on graph and speed

Demo 5
======

Percentiles

- naive approach? -> use a library! (apache-commons-math)
- ok for few datapoints
- not good for large datasets

Demo 6
======

- walk through aggegation verticle
- walk through percentiles verticle / pipeline
- experiment with parallelism (4, 8, 16) -> 4 allow disk use


Demo 7
======
- download a dataset from [http://api.bitcoincharts.com/v1/csv/]

columns
1) the trade's timestamp, column 
2) the price, column 
3) the volume of the trade

measure 20
