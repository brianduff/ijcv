java_library(
    name = "ijviewer",
    srcs = glob(["*.java"]),
    resources = glob(["*.png"]),
    deps = [
        "//lib:intellij"
    ]
)

java_binary(
    name = "Viewer",
    runtime_deps = [ ":ijviewer" ],
    main_class = "com.facebook.tools.intellij.ijviewer.IjViewer"
)