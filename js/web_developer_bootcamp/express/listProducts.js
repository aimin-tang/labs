var faker = require('faker');

console.log("======================");
console.log("WELCOME TO MY SHOP!");
console.log("======================");

for (var i = 0; i < 10; i++) {
    var n1 = faker.commerce.productName();
    var p1 = faker.commerce.price();
    console.log(n1 + " - $" + p1);
}

var express = require("express");
app = express();

app.get("/", function(req, res) {
    res.send("Hi there!");
});

app.get("/bye", function(req, res) {
    res.send("Goodbye!");
});

app.get("/dog", function(req, res) {
    res.send("MEOW!");
});

app.get("*", function(req, res) {
    res.send("You are a Star!");
});

app.listen(3000, 'localhost', function() {
    console.log("node started!");
}); 
