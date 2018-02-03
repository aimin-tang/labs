var mongoose = require("mongoose");

// Schema setup
var commentSchema = new mongoose.Schema({
    text: String,
    author: String
});

module.exports = mongoose.model("Comment", commentSchema);