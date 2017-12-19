var task_list = [];
var done = false;

while (!done) {
    task = prompt("What to do next?");

    if (task === "new") {
        content = prompt("Content of new task:");
        task_list.push(content);
    }

    if (task === "list") {
        task_list.forEach(function(task, i) {
            console.log(i + ": " + task);
        })
    }

    if (task === "delete") {
        index = prompt("Enter index to delete:");
        task_list = task_list.splice(index, 1);
        console.log("Item " + index + " deleted.");
    }

    if (task === "quit") {
        done = true;
        console.log("quiting...")
    }
}