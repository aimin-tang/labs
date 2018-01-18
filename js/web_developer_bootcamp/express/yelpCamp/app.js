var express = require("express");
var app = express();
var bodyParser = require("body-parser");

var campgrounds = [
    {
        name: "Salmon Creed",
        image: "https://upload.wikimedia.org/wikipedia/commons/d/d3/Takhlakh_Lake_Campground_Sign.JPG"
    },
    {
        name: "Granite Hill",
        image: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRypXGnfW0LfPFMg35_4xLK3AFsoSIKjpNPX3Jr9LEixoiAdLIvTQ"
    },
    {
        name: "Mountain Goat's Rest",
        image: "https://www.nps.gov/lavo/planyourvisit/images/Juniper-Lake-campsite.gif"
    }
];

app.use(bodyParser.urlencoded({extended: true}));
app.set("view engine", "ejs");

app.get("/", function (req, res) {
    res.render("landing");
});

app.get("/campgrounds", function (req, res) {
    res.render("campgrounds", {campgrounds: campgrounds});
});

app.get("/campgrounds/new", function (req, res) {
    res.render("new.ejs");
});

app.post("/campgrounds", function (req, res) {
    var name = req.body.name;
    var image = req.body.image;

    var newCampground = {name: name, image: image};
    campgrounds.push(newCampground);

    // redirect back to campgrounds
    res.redirect("/campgrounds");
});

app.listen(3000, 'localhost', function () {
    console.log("The Yelp Camp Server has started!");
});