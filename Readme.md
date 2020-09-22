# IntelliJ Cache viewer

This allows you to see the content of the IntelliJ (or Android Studio) cache. It has only been tested with AS 4.0 and IJ 2020.*.

<img src="https://raw.githubusercontent.com/brianduff/ijcv/master/screenshot.png" width="650" />

## Building

You'll need bazel. bazel.io has instructions for installing. On my mac, I just:

```
brew install bazel
```

After it's installed, compile and run with:

```
bazel run src/main/java/com/facebook/tools/intellij/ijviewer:Viewer
```

## Configuring

Currently the path to the cache directory is... er... hardcoded in the code. It will work ok if you happen to be on a Mac, and you happen to want to look at the cache of Android Studio 4.0 specifically. I'll eventually get around to fixing this, but in the meantime, you can head over to line 126 of `IjViewer.java` to change it if you want.

## A note on startup

It might take a short while (2-3 seconds) to start depending on how big your cache is. I learned the hard way that the read APIs for IJ's persistence files will sometimes write corruption magic into the files, which will cause IJ to have to throw them away and regenerate them the next time you run it. This is annoying, so the program copies the entire cache directory to a temporary directory, then works on this copy instead of the original.
