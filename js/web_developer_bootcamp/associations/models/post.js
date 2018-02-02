var mongoose = require("mongoose");

// post
var postSchema = new mongoose.Schema({
    title: String,
    content: String
});

module.export = mongoose.model("Post", postSchema);
