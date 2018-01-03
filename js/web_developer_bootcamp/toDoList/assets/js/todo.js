$("h1").css("color", "purple");

// check off to do item by clicking
$("li").click(function () {
    $(this).toggleClass('completed');
});