var express = require("express");
var app = express();

app.get("/", function (req, res) {
    res.send("Hi there, welcome to my assignment!");
});

app.get("/speak/:animal", function (req, res) {
    if (req.params.animal === 'pig') {
        res.send("The pig says Oink!");
    } else if (req.params.animal === 'cow') {
        res.send("The cow says Moo!");
    } else if (req.params.animal === 'dog') {
        res.send("The dog says Woof Woof!");
    } else {
        res.send("The unknown " + req.params.animal + " says something!");
    }
});

app.get("/repeat/:str/:num", function (req, res) {
    result = "";
    for (var i = 0; i < parseInt(req.params.num) - 1; i++) {
        result += req.params.str + " ";
    }
    result += req.params.str;

    res.send(result);
});

app.get("*", function (req, res) {
    res.send("Sorry, page not found...What are you doing in your life?");
});

app.listen(3000, 'localhost');