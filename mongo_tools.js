var db = db.getSiblingDB("rtm");

function countDistinct(){
    db.measurements.distinct("t.name")
        .map(function(name){return { name : name, count : db.measurements.count({"t.name":name})}})
        .sort(function(o1,o2){return o1.count > o2.count})
        .forEach(function(o){print(JSON.stringify(o))})
}
