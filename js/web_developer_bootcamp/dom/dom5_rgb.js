var squares = document.querySelectorAll('.square');
var headerColor = document.querySelector('#headerColor');
var verdict = document.querySelector("#verdict");
var h1 = document.querySelector("h1");
var resetBtn= document.querySelector("#resetBtn");
var easyBtn= document.querySelector("#easyBtn");
var hardBtn= document.querySelector("#hardBtn");
var colors;
var squaresInUse = 6;
var pickedColor = undefined;

function generateRandomColors(numSquares) {
    colors = [];
    for (var i = 0; i < numSquares; i++) {
        color = "rgb(";
        color += generateRandomNumber(256);
        color += ", ";
        color += generateRandomNumber(256);
        color += ", ";
        color += generateRandomNumber(256);
        color += ")";
        colors.push(color);
    }
}

function generateRandomNumber(upperLimit) {
    return Math.floor(Math.random() * upperLimit);
}

function newSquareColors() {
    for (var i = 0; i < squaresInUse; i++) {
        squares[i].style.backgroundColor = colors[i];
    }
}

function pickAColor() {
    num = generateRandomNumber(squaresInUse);
    pickedColor = colors[num];
    console.log(pickedColor);
}

function updateH1Color() {
    headerColor.textContent = pickedColor;
}

function squaresInPickedColor(color) {
    for (var i = 0; i < squaresInUse; i++) {
        squares[i].style.backgroundColor = color;
    }
}

function h1InPickedColor(color) {
    h1.style.backgroundColor = color;
}

function updateSquareEvHandlers() {
    for (var i = 0; i < squaresInUse; i++) {
        squares[i].addEventListener("click", function () {
            if (this.style.backgroundColor === pickedColor) {
                verdict.textContent = "correct!";
                squaresInPickedColor(pickedColor);
                h1InPickedColor(pickedColor);
                resetBtn.textContent = "Play again";
            } else {
                this.style.backgroundColor = "#232323";
                verdict.textContent = "wrong!";
            }
        })
    }
}

function eraseSecondRow() {
    for (var i = 3; i < 6; i++) {
        // squares[i].style.backgroundColor = "#232323";
        squares[i].style.display = "none";
    }
}

function showSecondRow() {
    for (var i = 3; i < 6; i++) {
        // squares[i].style.backgroundColor = "#232323";
        squares[i].style.display = "block";
    }
}

resetBtn.addEventListener("click", function () {
    createNewPage();
    h1.style.backgroundColor = "steelblue";
})

easyBtn.addEventListener("click", function () {
    easyBtn.classList.add('selected');
    hardBtn.classList.remove('selected');
    squaresInUse = 3;
    createNewPage();
    eraseSecondRow();
    h1.style.backgroundColor = "steelblue";
})

hardBtn.addEventListener("click", function () {
    hardBtn.classList.add('selected');
    easyBtn.classList.remove('selected');
    squaresInUse = 6;
    createNewPage();
    showSecondRow();
    h1.style.backgroundColor = "steelblue";
})

function createNewPage() {
    generateRandomColors(6);
    newSquareColors();
    pickAColor();
    updateH1Color();
    updateSquareEvHandlers();
    resetBtn.textContent = "New colors";
    verdict.textContent = "";
}

createNewPage();
