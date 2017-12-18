var task_list = [];
var done = false;

while (!done) {
    task = prompt("What to do next?");

    if (task === "new") {
        content = prompt("Content of new task:");
        task_list.push(content);
    }

    if (task === "list") {
        console.log(task_list);
    }

    if (task === "quit") {
        done = true;
        console.log("quiting...")
    }
}