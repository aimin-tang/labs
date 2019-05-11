var express = require("express");
var app = express();
var bodyParser = require("body-parser");
var mongoose = require("mongoose");
var passport = require("passport");
var LocalStrategy = require("passport-local");
var Campground = require("./models/campground");
var Comment = require("./models/comment");
var User = require("./models/user");
var seedDB = require("./seeds2");

// requiring routes
var commentRoutes = require("./routes/comments");
var campgroundRoutes = require("./routes/campgrounds");
var indexRoutes = require("./routes/index");

// seedDB();  // seed the database

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
app.use(express.static(__dirname + "/public"));

app.use(require("express-session")({
    secret: "Once again Rusty wins cutest dog!",
    resave: false,
    saveUninitialized: false
}));
app.use(passport.initialize());
app.use(passport.session());
passport.use(new LocalStrategy(User.authenticate()));
passport.serializeUser(User.serializeUser());
passport.deserializeUser(User.deserializeUser());

app.use(function (req, res, next) {
    res.locals.currentUser = req.user;
    next();
});

app.use("/campgrounds/:id/comments", commentRoutes);
app.use("/campgrounds", campgroundRoutes);
app.use("/", indexRoutes);

app.listen(3000, 'localhost', function () {
    console.log("The Yelp Camp Server has started!");
});