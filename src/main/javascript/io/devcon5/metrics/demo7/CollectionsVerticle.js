/**
 * This Verticle delivers structural information about the database
 */
var MongoClient = require("vertx-mongo-js/mongo_client");
var config = Vertx.currentContext().config();
var dbname = config.mongo.db_name;
var mongo = MongoClient.createShared(vertx, config.mongo);

var collectionNames = [];
//TODO support multiple DBs

console.log("Registering " + dbname);
vertx.eventBus().consumer("/" + dbname, function (msg) {
    msg.reply({});
});


//initialize the collection name array and register a prop name handler for each collection
mongo.getCollections(function(res, res_err) {
    if(res_err == null) {
        res.forEach(function(c){
            collectionNames.push(c);
            vertx.eventBus().consumer("/" + dbname + '/' + c + '/propnames', fieldsResponse(c));
            vertx.eventBus().consumer("/" + dbname + '/' + c + '/distinct', namesResponse(c));
        });
    } else {
        console.error(res_err);
    }
});

// register handler to query for all collection
vertx.eventBus().consumer("/" + dbname + config.address, function (msg) {
    msg.reply(collectionNames);
});

/**
 * Response handler to query for propertynames of objects in the specified collection. The method uses a findOne query
 * and collects the field names of the resulting object
 * @param collection
 * @returns {Function}
 */
function fieldsResponse (collection) {

    /**
     * Creates a fully qualified name of a given propery by prepending the prefix. If no prefix is set,
     * the name itself is returned
     * @param prefix
     *  optional prefix
     * @param name
     *  the name.
     * @returns {*} the either prefix.name or just name
     */
    function fq(prefix, name){
        if(typeof prefix === 'undefined'){
            return name;
        }
        return prefix + '.' + name;
    }

    /**
     * Method to collect the property names of the object. The method returns an array of names of the object. If
     * the object contains nested objects, the property names of the nested objects are returned as well, prefixed
     * with the name of the property referencing the nested object.
     * For example, the following object
     * <pre>
     *     {
     *       a : 2,
     *       b : {
     *         c : 1
     *       }
     *     }
     * </pre>
     * will result in
     * <pre>
     *    [ "a", "b.c"]
     * </pre>
     * @param obj
     *  the object to collect the property names from
     * @param prefix
     *  the prefix to use for nested properties. Leave undefined for the root object
     * @returns {Array}
     */
    function collectPropNames(obj, prefix){
        var propNames = [];
        for (var property in obj) {
            if (obj.hasOwnProperty(property)) {
                var val = obj[property];
                if(typeof val === 'object'){
                    propNames = propNames.concat(collectPropNames(val, property))
                } else {
                    propNames.push(fq(prefix, property));
                }
            }
        }
        return propNames;
    }

    // the method that performs the actual lookup and the result transformation
    return function(msg) {
        mongo.findOne(collection, {}, {_id: 0}, function(res, res_err){
            if(res_err == null) {
                msg.reply(collectPropNames(res));
            } else {
                msg.reply([])
            }
        })
    }
}

function namesResponse(collection){
    // the method that performs the actual lookup and the result transformation
    return function(msg) {
        console.log(JSON.stringify(msg));
        var fieldName = msg.body().property;
        mongo.distinct(collection, fieldName, 'java.lang.String' ,function(res, res_err){
            if(res_err == null) {
                msg.reply(res);
            } else {
                msg.reply([])
            }
        })
    }
}

