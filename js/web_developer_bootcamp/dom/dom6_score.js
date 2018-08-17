var p1Score = 0;
var p2Score = 0;
var playTill = 5;
var gameOver = false;
var p1Button = document.querySelector("#p1Button");
var p2Button = document.querySelector("#p2Button");
var resetButton = document.querySelector("#resetButton");
var p1Display = document.querySelector("#p1Display");
var p2Display = document.querySelector("#p2Display");
playTillInput = document.querySelector("#playTill");

function getPlayTill () {
    playTill = Number(playTillInput.value) || 5;
    return playTill;
}

function reset() {
    p1Score = 0;
    p2Score = 0;
    playTill = getPlayTill();
    gameOver = false;
    p1Display.textContent = p1Score;
    p2Display.textContent = p2Score;
    p1Display.classList.remove("green");
    p2Display.classList.remove("green");
}
reset();

p1Button.addEventListener("click", function () {
    if (!gameOver) {
        p1Score++;
        p1Display.textContent = p1Score;
    }
    if (p1Score === playTill) {
        p1Display.classList.add("green");
        gameOver = true;
    }
});
p2Button.addEventListener("click", function () {
    if (!gameOver) {
        p2Score++;
        p2Display.textContent = p2Score;
    }
    if (p2Score === playTill) {
        p2Display.classList.add("green");
        gameOver = true;
    }
});
resetButton.addEventListener("click", function () {
    reset();
});
playTillInput.addEventListener("change", function () {
    reset();
});