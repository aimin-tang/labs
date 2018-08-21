var horizontal_fields = ["session", "computer operator 1", "timing console operator 1"];
var vertical_fields = ["1: 1/1/2019 9am - 12pm", "2: 1/2/2019 9am - 12pm"]

function fieldToTdHtml (field) {
    return "<td>" + field + "</td>";
}

// draw thead
function drawThead (fields) {
    result = "";
    result += "<tr>";
    for (var i = 0; i < fields.length; i++) {
        result += fieldToTdHtml(fields[i]);
    }
    result += "</tr>";
    return result;
}

$('thead').html(drawThead(horizontal_fields));

// draw remaining rows
function drawTbody (horizontal_fields, vertical_fields) {
    result = "";
    for (var i = 0; i < vertical_fields.length; i++) {
        result += "<tr>";
        result += fieldToTdHtml(vertical_fields[i]);
        for (var j = 1; j < horizontal_fields.length; j++) {
            result += fieldToTdHtml("");
        }
        result += "</tr>";
    }
    return result;
}

$('tbody').html(drawTbody(horizontal_fields, vertical_fields));