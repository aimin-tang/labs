function printReverse(arr) {
    l = arr.length - 1;
    for(var i=l; i>=0; i--) {
        console.log(arr[i]);
    }
}

function isUniform(arr) {
    for(var i=1; i<arr.length; i++) {
        if (arr[i] !== arr[0]) {
            return false
        }
    }
    return true
}

function sumArray(arr) {
    sum = 0;
    for(var i=0; i<arr.length; i++) {
        sum += arr[i];
    }
    return sum
}

function max(arr) {
    max = arr[0];
    for(var i=1; i<arr.length; i++) {
        if (arr[i] > arr[0]) {
            max = arr[i];
        }
    }
    return max
}