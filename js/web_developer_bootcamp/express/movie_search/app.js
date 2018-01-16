var express = require("express");
var app = express();
var request = require("request");
app.set("view engine", "ejs");

app.get("/results", function (req, res) {
    result = request("http://omdbapi.com/?s=california&apikey=thewdb",
        function (error, response, body) {
            if (!error && response.statusCode == 200) {
                var data = JSON.parse(body);
                res.render("results", {data: data});
            }
        });
});

app.listen(3000, 'localhost', function () {
    console.log("Movie app!")
});