
var index = lunr(function () {
    this.field('body');
    this.ref('url');
});

var documentTitles = {};



documentTitles["index.html#preface"] = "Preface";
index.add({
    url: "index.html#preface",
    title: "Preface",
    body: "# Preface  Welcome to the Jet Reference Manual. This manual includes concepts, instructions, and samples to guide you on how to use Jet and build Jet applications.  As the reader of this manual, you must be familiar with the Java programming language and you should have installed your preferred Integrated Development Environment (IDE).     "
});

documentTitles["index.html#code-deployment"] = "Code Deployment";
index.add({
    url: "index.html#code-deployment",
    title: "Code Deployment",
    body: "# Code Deployment Jet enables you to distribute various resources to be used in your processors. Those resources can be a class files, JAR files or any type of files.    "
});


