package com.facebook.tools.intellij.ijviewer.ui;

import java.awt.Image;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.ImageIcon;

final class IconLoader {
  private Map<String, Optional<Icon>> cache = new HashMap<>();

  public Optional<Icon> getIcon(String name) {
    Optional<Icon> result = cache.get(name);
    if (result != null) {
      return result;
    }

    String fileName = "image/" + name + ".png";

    Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource(fileName));
    result = Optional.of(new ImageIcon(image));

    cache.put(name, result);
    return result;
  }
}
