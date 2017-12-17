var age = prompt("What is your age?");

if (age < 0) {
    console.log("invalid age!");
}

else {
    if (age == 21) {
        console.log("Happy 21st birthday!");
    }
    if (age % 2 == 1) {
        console.log("Your age is odd!")
    }
    if (Math.pow(Math.floor(Math.sqrt(age)), 2) == age) {
        console.log("Perfect age!")
    }
}