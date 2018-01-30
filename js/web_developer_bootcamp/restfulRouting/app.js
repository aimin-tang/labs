var bodyParser = require("body-parser");
var mongoose = require("mongoose");
var express = require("express");
app = express();

// title
// image
// body
// created

// App config
mongoose.connect("mongodb://localhost/restful_blog_app");
app.set("view engine", "ejs");
app.use(express.static("public"));
app.use(bodyParser.urlencoded({extended: true}));

// Mongoose config
var blogSchema = new mongoose.Schema({
    title: String,
    image: String,
    body: String,
    created: {type: Date, default: Date.now}
});
var Blog = mongoose.model("Blog", blogSchema);

/*
Blog.create({
    title: "Test Blog",
    image: "https://images.unsplash.com/photo-1502795147815-88376c4e38f9?ixlib=rb-0.3.5&s=219dcf7fdfc93cbc4a6ec1bea3272c08&auto=format&fit=crop&w=1950&q=80",
    body: "This is a blog post"
});
*/

// Restful routes
// index
app.get("/", function (req, res) {
    res.redirect("/blogs");
});

app.get("/blogs", function (req, res) {
    Blog.find({}, function (err, blogs) {
        if (err) {
            console.log("Error!");
        } else {
            res.render("index", {blogs: blogs});
        }
    })
});

// new
app.get("/blogs/new", function (req, res) {
    res.render("new");
});

// create
app.post("/blogs", function (req, res) {
    // create blog
    Blog.create(req.body.blog, function (err, newBlog) {
        if (err) {
            res.render("new");
        } else {
            res.redirect("/blogs");
        }
    })
    // redirect to index
});

app.listen(3000, 'localhost', function () {
    console.log("Server is running!");
});