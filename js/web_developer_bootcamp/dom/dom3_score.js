var goal = 5;
var p1Point = 0;
var p2Point = 0;
var gameOver = false;

var h1Span1 = document.querySelector('#p1Score');
var h1Span2 = document.querySelector('#p2Score');

var inputBox = document.querySelector('input');

var playerButtons = document.querySelectorAll('.player_button');
var p1 = playerButtons[0];
var p2 = playerButtons[1];

var resetButton = document.querySelectorAll('button')[2];

inputBox.addEventListener("click", function() {
    console.log("input clicked!")
})

p1.addEventListener("click", function() {
    if (!gameOver) {
        p1Point++;
        h1Span1.textContent = p1Point;
    }
    if (p1Point === goal) {
        gameOver = true;
    }
})

p2.addEventListener("click", function() {
    if (!gameOver) {
        p2Point++;
        h1Span2.textContent = p2Point;
    }
    if (p2Point === goal) {
        gameOver = true;
    }
})

resetButton.addEventListener("click", function() {
    p1Point = 0;
    p2Point = 0;
    h1Span1.textContent = p1Point;
    h1Span2.textContent = p2Point;
    gameOver = false;
})