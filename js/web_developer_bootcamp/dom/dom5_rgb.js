var colors = generateRandomColors(6);

var squares = document.querySelectorAll('.square');
var pickedColor = pickColor();
var colorDisplay = document.getElementById('colorDisplay');
var messageDisplay = document.querySelector('#message');
var h1 = document.querySelector('h1');
var resetButton = document.querySelector('#reset');

function updateTiles() {
    colorDisplay.textContent = pickedColor;
    resetH1Color();

    for (var i = 0; i < squares.length; i++) {
        squares[i].style.backgroundColor = colors[i];
    }
}

function updateTilesEvents() {
    for (var i = 0; i < squares.length; i++) {
        squares[i].addEventListener("click", function () {
            var clickedColor = this.style.backgroundColor;
            if (clickedColor === pickedColor) {
                messageDisplay.textContent = 'Correct!';
                changeColors(clickedColor);
                h1.style.backgroundColor = pickedColor;
                resetButton.textContent = 'Play again?';
            } else {
                this.style.backgroundColor= '#232323';
                messageDisplay.textContent = 'Try Again!';
            }
        })
    }
}

function resetH1Color() {
    h1.style.backgroundColor = '#232323';
}

function changeColors(color) {
    for (var i=0; i<squares.length;i++) {
        squares[i].style.backgroundColor = color;
    }
}

function randomNum(upperLimit) {
    return Math.floor(Math.random() * upperLimit);
}

function pickColor() {
    var r_num = randomNum(colors.length);
    return colors[r_num];
}

function generateRandomColors(num) {
    var colors = [];
    for (var i=0; i<num; i++) {
        str = "rgb(";
        str += randomNum(256);
        str += ", ";
        str += randomNum(256);
        str += ", ";
        str += randomNum(256);
        str += ")";
        colors.push(str);
    }
    return colors;
}

resetButton.addEventListener("click", function () {
    colors = generateRandomColors(6);
    pickedColor = pickColor();
    updateTiles();
    this.textContent = 'New colors';
})

updateTiles();
updateTilesEvents();