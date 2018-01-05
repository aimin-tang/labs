// check off to do item by clicking
$("ul").on("click", "li", function () {
    $(this).toggleClass('completed');
});

$("ul").on("click", "span", function(event) {
    $(this).parent().fadeOut(500, function() {
        $(this).remove();
    });
    event.stopPropagation();
});

$("input[type='text']").keypress(function (event) {
  if (event.which === 13) {
      var newToDoItem = $(this).val();
      $(this).val("");
      $("ul").append("<li><i class='fa fa-trash'></i> " + newToDoItem + "</li>");
  }
});

$(".fa-plus").click(function() {
    $("input[type='text']").fadeToggle();
})