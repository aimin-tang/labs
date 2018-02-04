var express = require("express");
var app = express();
var bodyParser = require("body-parser");
var mongoose = require("mongoose");
var Campground = require("./models/campground");
var Comment = require("./models/comment");
var seedDB = require("./seeds2");

seedDB();

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
            res.render("campgrounds/index", {campgrounds: campgrounds});
        }
    });
});

app.get("/campgrounds/new", function (req, res) {
    res.render("campgrounds/new.ejs");
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
            res.redirect("campgrounds/campgrounds");
        }
    });
});

app.get("/campgrounds/:id", function (req, res) {
    Campground.findById(req.params.id).populate("comments").exec(function (err, foundCampground) {
        if (err) {
            console.log(err);
        } else {
            res.render("campgrounds/show", {campground: foundCampground});
        }
    });
});

// comments routes
// ==================
app.get("/campgrounds/:id/comments/new", function (req, res) {
    Campground.findById(req.params.id, function (err, campground) {
        if (err) {
            console.log(err);
        } else{
            res.render("comments/new", {campground: campground});
        }
    });
});

app.post("/campgrounds/:id/comments", function (req, res) {
    Campground.findById(req.params.id, function (err, campground) {
        if (err) {
            console.log(err);
            res.redirect("/campgrounds");
        } else {
            Comment.create(req.body.comment, function (err, comment) {
                if (err) {
                    console.log(err);
                } else {
                    campground.comments.push(comment);
                    campground.save();
                    res.redirect('/campgrounds/' + campground._id);
                }
            })
        }
    })
});

app.listen(3000, 'localhost', function () {
    console.log("The Yelp Camp Server has started!");
});