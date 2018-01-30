var express = require("express");
var app = express();
var bodyParser = require("body-parser");
var mongoose = require("mongoose");

mongoose.connect("mongodb://localhost/yelp_camp");

var campgrounds = [
    {
        name: "Salmon Creed",
        image: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRypXGnfW0LfPFMg35_4xLK3AFsoSIKjpNPX3Jr9LEixoiAdLIvTQ"
    },
    {
        name: "Granite Hill",
        image: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRypXGnfW0LfPFMg35_4xLK3AFsoSIKjpNPX3Jr9LEixoiAdLIvTQ"
    },
    {
        name: "Mountain Goat's Rest",
        image: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRypXGnfW0LfPFMg35_4xLK3AFsoSIKjpNPX3Jr9LEixoiAdLIvTQ"
    }
];

// Schema setup
var campgroundSchema = new mongoose.Schema({
    name: String,
    image: String,
    description: String
});

var Campground = mongoose.model("Campground", campgroundSchema);

/*Campground.create({
    name: "Logside",
    image: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRypXGnfW0LfPFMg35_4xLK3AFsoSIKjpNPX3Jr9LEixoiAdLIvTQ",
    description: "This is Logside"
}, function (err, campground) {
    if (err) {
        console.log(err);
    } else {
        console.log("New campground:");
        console.log(campground);
    }
});*/

app.use(bodyParser.urlencoded({extended: true}));
app.set("view engine", "ejs");

app.get("/", function (req, res) {
    res.render("landing");
});

app.get("/campgrounds", function (req, res) {
    Campground.find({}, function (err, campgrounds) {
        if (err) {
            console.log(err);
        } else {
            res.render("index", {campgrounds: campgrounds});
        }
    });
});

app.get("/campgrounds/new", function (req, res) {
    res.render("new.ejs");
});

app.post("/campgrounds", function (req, res) {
    var name = req.body.name;
    var image = req.body.image;
    var description = req.body.description;

    var newCampground = {name: name, image: image, description: description};
    Campground.create(newCampground, function (err, newlyCreated) {
        if (err) {
            console.log(err);
        } else {
            // redirect back to campgrounds
            res.redirect("/campgrounds");
        }
    });
});

app.get("/campgrounds/:id", function (req, res) {
    Campground.findById(req.params.id, function (err, foundCampground) {
        if (err) {
            console.log(err);
        } else {
            res.render("show", {campground: foundCampground});
        }
    });
});

app.listen(3000, 'localhost', function () {
    console.log("The Yelp Camp Server has started!");
});